/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.myjdiproject.tools.jdi;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.myjdiproject.jdi.BooleanValue;
import com.myjdiproject.jdi.ByteValue;
import com.myjdiproject.jdi.CharValue;
import com.myjdiproject.jdi.ClassNotLoadedException;
import com.myjdiproject.jdi.DoubleValue;
import com.myjdiproject.jdi.FloatValue;
import com.myjdiproject.jdi.IntegerValue;
import com.myjdiproject.jdi.InternalException;
import com.myjdiproject.jdi.LongValue;
import com.myjdiproject.jdi.ModuleReference;
import com.myjdiproject.jdi.ObjectCollectedException;
import com.myjdiproject.jdi.PathSearchingVirtualMachine;
import com.myjdiproject.jdi.PrimitiveType;
import com.myjdiproject.jdi.ReferenceType;
import com.myjdiproject.jdi.RuleIndexData;
import com.myjdiproject.jdi.ShortValue;
import com.myjdiproject.jdi.StringReference;
import com.myjdiproject.jdi.ThreadGroupReference;
import com.myjdiproject.jdi.ThreadReference;
import com.myjdiproject.jdi.Type;
import com.myjdiproject.jdi.VMDisconnectedException;
import com.myjdiproject.jdi.VirtualMachineManager;
import com.myjdiproject.jdi.VoidValue;
import com.myjdiproject.jdi.connect.spi.Connection;
import com.myjdiproject.jdi.event.EventQueue;
import com.myjdiproject.jdi.request.BreakpointRequest;
import com.myjdiproject.jdi.request.EventRequest;
import com.myjdiproject.jdi.request.EventRequestManager;

class VirtualMachineImpl extends MirrorImpl implements PathSearchingVirtualMachine, ThreadListener {
    private static final int DISPOSE_THRESHOLD = 50;

    public final int sizeofFieldRef;
    public final int sizeofMethodRef;
    public final int sizeofObjectRef;
    public final int sizeofClassRef;
    public final int sizeofFrameRef;
    public final int sizeofModuleRef;

    final int sequenceNumber;

    private final TargetVM target;
    private final EventQueueImpl eventQueue;
    private final EventRequestManagerImpl internalEventRequestManager;
    private final EventRequestManagerImpl eventRequestManager;
    final VirtualMachineManagerImpl vmManager;
    private final ThreadGroup threadGroupForJDI;

    private final Map<Long, ReferenceType> typesByID = new ConcurrentHashMap<>(50000);
    private final Set<ReferenceType> typesBySignature = new HashSet<>();
    private boolean retrievedAllTypes = false;

    private Map<Long, ModuleReference> modulesByID;

    private String defaultStratum = null;

    private final Map<Long, SoftObjectReference> objectsByID = new HashMap<>();
    private final ReferenceQueue<ObjectReferenceImpl> referenceQueue = new ReferenceQueue<>();
    private final List<SoftObjectReference> batchedDisposeRequests = Collections.synchronizedList(new ArrayList<>(DISPOSE_THRESHOLD + 10));

    private JDWP.VirtualMachine.Version versionInfo;
    private JDWP.VirtualMachine.ClassPaths pathInfo;
    private JDWP.VirtualMachine.Capabilities capabilities = null;
    private JDWP.VirtualMachine.CapabilitiesNew capabilitiesNew = null;

    final BooleanTypeImpl theBooleanType;
    final ByteTypeImpl theByteType;
    final CharTypeImpl theCharType;
    final ShortTypeImpl theShortType;
    final IntegerTypeImpl theIntegerType;
    final LongTypeImpl theLongType;
    final FloatTypeImpl theFloatType;
    final DoubleTypeImpl theDoubleType;
    final VoidTypeImpl theVoidType;
    final VoidValueImpl voidVal;

    final BooleanValueImpl booleanTrue;
    final BooleanValueImpl booleanFalse;

    private final Process process;
    private final VMState state = new VMState(this);
    private final Object initMonitor = new Object();
    private boolean initComplete = false;
    private boolean shutdown = false;

    VirtualMachineImpl(VirtualMachineManager manager, Connection connection, Process process, int sequenceNumber) {
        super(null);
        vm = this;
        this.vmManager = (VirtualMachineManagerImpl)manager;
        this.process = process;
        this.sequenceNumber = sequenceNumber;
        threadGroupForJDI = new ThreadGroup(vmManager.mainGroupForJDI(), "JDI [" + this.hashCode() + "]");
        target = new TargetVM(this, connection);

        EventQueueImpl internalEventQueue = new EventQueueImpl(this, target);
        new InternalEventHandler(this, internalEventQueue);

        eventQueue = new EventQueueImpl(this, target);
        eventRequestManager = new EventRequestManagerImpl(this);

        target.start();

        JDWP.VirtualMachine.IDSizes idSizes;
        try {
            idSizes = JDWP.VirtualMachine.IDSizes.process(vm);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        sizeofFieldRef  = idSizes.fieldIDSize;
        sizeofMethodRef = idSizes.methodIDSize;
        sizeofObjectRef = idSizes.objectIDSize;
        sizeofClassRef = idSizes.referenceTypeIDSize;
        sizeofFrameRef  = idSizes.frameIDSize;
        sizeofModuleRef = idSizes.objectIDSize;

        internalEventRequestManager = new EventRequestManagerImpl(this);
        EventRequest er = internalEventRequestManager.createClassPrepareRequest();
        er.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        er.enable();
        er = internalEventRequestManager.createClassUnloadRequest();
        er.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        er.enable();

        notifyInitCompletion();

        theBooleanType = new BooleanTypeImpl(this);
        theByteType = new ByteTypeImpl(this);
        theCharType = new CharTypeImpl(this);
        theShortType = new ShortTypeImpl(this);
        theIntegerType = new IntegerTypeImpl(this);
        theLongType = new LongTypeImpl(this);
        theFloatType = new FloatTypeImpl(this);
        theDoubleType = new DoubleTypeImpl(this);
        theVoidType = new VoidTypeImpl(this);
        voidVal = new VoidValueImpl(this);

        booleanTrue = new BooleanValueImpl(this, true);
        booleanFalse = new BooleanValueImpl(this, false);
    }

    private void notifyInitCompletion() {
        synchronized (initMonitor) {
            initComplete = true;
            initMonitor.notifyAll();
        }
    }

    void waitInitCompletion() {
        synchronized (initMonitor) {
            while (!initComplete) {
                try {
                    initMonitor.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    VMState state() {
        return state;
    }

    public boolean threadResumable(ThreadAction action) {
        state.thaw(action.thread());
        return true;
    }

    EventRequestManagerImpl getInternalEventRequestManager() {
        return internalEventRequestManager;
    }

    public boolean equals(Object obj) {
        return this == obj;
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }

    public List<ModuleReference> allModules() {
        List<ModuleReference> modules = retrieveAllModules();
        return Collections.unmodifiableList(modules);
    }

    public List<ReferenceType> classesByName(String className) {
        return classesBySignature(JNITypeParser.typeNameToSignature(className));
    }

    List<ReferenceType> classesBySignature(String signature) {
        List<ReferenceType> list;
        if (retrievedAllTypes) {
            list = findReferenceTypes(signature);
        } else {
            list = retrieveClassesBySignature(signature);
        }
        return Collections.unmodifiableList(list);
    }

    public List<ReferenceType> allClasses() {
        if (!retrievedAllTypes) {
            retrieveAllClasses();
        }

        synchronized (this) {
            return new ArrayList<>(typesBySignature);
        }
    }

    // SCANNER ADDED
    // Returns only the prepared classes that the target VM's RuleIndex class filter keeps;
    // dropped classes arrive separately via a non-suspending IgnoredClassesEvent. Unlike
    // allClasses() this is not cached and does not mark all types as retrieved.
    public List<ReferenceType> allClassesFiltered() {
        try {
            return new ArrayList<>(JDWP.VirtualMachine.AllClassesFiltered.process(vm).classes);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public void forEachClass(Consumer<ReferenceType> action) {
        for (ReferenceType type : allClasses()) {
            try {
                action.accept(type);
            } catch (ObjectCollectedException ex) {
            }
        }
    }

    public void redefineClasses(Map<? extends ReferenceType, byte[]> classToBytes) {
        if (!canRedefineClasses()) {
            throw new UnsupportedOperationException();
        }

        vm.state().thaw();

        try {
            JDWP.VirtualMachine.RedefineClasses.process(vm, classToBytes);
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
            case JDWP.Error.INVALID_CLASS_FORMAT:
                throw new ClassFormatError("class not in class file format");
            case JDWP.Error.CIRCULAR_CLASS_DEFINITION:
                throw new ClassCircularityError("circularity has been detected while initializing a class");
            case JDWP.Error.FAILS_VERIFICATION:
                throw new VerifyError("verifier detected internal inconsistency or security problem");
            case JDWP.Error.UNSUPPORTED_VERSION:
                throw new UnsupportedClassVersionError("version numbers of class are not supported");
            case JDWP.Error.ADD_METHOD_NOT_IMPLEMENTED:
                throw new UnsupportedOperationException("add method not implemented");
            case JDWP.Error.SCHEMA_CHANGE_NOT_IMPLEMENTED:
                throw new UnsupportedOperationException("schema change not implemented");
            case JDWP.Error.HIERARCHY_CHANGE_NOT_IMPLEMENTED:
                throw new UnsupportedOperationException("hierarchy change not implemented");
            case JDWP.Error.DELETE_METHOD_NOT_IMPLEMENTED :
                throw new UnsupportedOperationException("delete method not implemented");
            case JDWP.Error.CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED:
                throw new UnsupportedOperationException("changes to class modifiers not implemented");
            case JDWP.Error.METHOD_MODIFIERS_CHANGE_NOT_IMPLEMENTED:
                throw new UnsupportedOperationException("changes to method modifiers not implemented");
            case JDWP.Error.CLASS_ATTRIBUTE_CHANGE_NOT_IMPLEMENTED:
                throw new UnsupportedOperationException("changes to class attribute not implemented");
            case JDWP.Error.NAMES_DONT_MATCH:
                throw new NoClassDefFoundError("class names do not match");
            default:
                throw exc.toJDIException();
            }
        }

        List<BreakpointRequest> toDelete = new ArrayList<>();
        EventRequestManager erm = eventRequestManager();
        for (BreakpointRequest req : erm.breakpointRequests()) {
            if (classToBytes.containsKey(req.location().declaringType())) {
                toDelete.add(req);
            }
        }
        erm.deleteEventRequests(toDelete);

        for (ReferenceType rt : classToBytes.keySet()) {
            ReferenceTypeImpl rti = (ReferenceTypeImpl) rt;
            rti.noticeRedefineClass();
        }
    }

    public List<ThreadReference> allThreads() {
        return state.allThreads();
    }

    public List<ThreadGroupReference> topLevelThreadGroups() {
        return state.topLevelThreadGroups();
    }

    PacketStream sendResumingCommand(CommandSender sender) {
        return state.thawCommand(sender);
    }

    void notifySuspend() {
        state.freeze();
    }

    public void suspend() {
        try {
            JDWP.VirtualMachine.Suspend.process(vm);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        notifySuspend();
    }

    public void resume() {
        CommandSender sender = () -> JDWP.VirtualMachine.Resume.enqueueCommand(vm);
        try {
            PacketStream stream = state.thawCommand(sender);
            JDWP.VirtualMachine.Resume.waitForReply(stream);
        } catch (VMDisconnectedException exc) {
        } catch (JDWPException exc) {
            switch (exc.errorCode()) {
                case JDWP.Error.VM_DEAD:
                    return;
                default:
                    throw exc.toJDIException();
            }
        }
    }

    public EventQueue eventQueue() {
        return eventQueue;
    }

    public EventRequestManager eventRequestManager() {
        return eventRequestManager;
    }

    EventRequestManagerImpl eventRequestManagerImpl() {
        return eventRequestManager;
    }

    public BooleanValue mirrorOf(boolean value) {
        return value ? booleanTrue : booleanFalse;
    }

    public ByteValue mirrorOf(byte value) {
        return new ByteValueImpl(this, value);
    }

    public CharValue mirrorOf(char value) {
        return new CharValueImpl(this, value);
    }

    public ShortValue mirrorOf(short value) {
        return new ShortValueImpl(this, value);
    }

    public IntegerValue mirrorOf(int value) {
        return new IntegerValueImpl(this, value);
    }

    public LongValue mirrorOf(long value) {
        return new LongValueImpl(this, value);
    }

    public FloatValue mirrorOf(float value) {
        return new FloatValueImpl(this, value);
    }

    public DoubleValue mirrorOf(double value) {
        return new DoubleValueImpl(this, value);
    }

    public StringReference mirrorOf(String value) {
        try {
            return JDWP.VirtualMachine.CreateString.process(vm, value).stringObject;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public VoidValue mirrorOfVoid() {
        return voidVal;
    }

    public long[] instanceCounts(List<? extends ReferenceType> classes) {
        if (!canGetInstanceInfo()) {
            throw new UnsupportedOperationException("target does not support getting instances");
        }
        try {
            return JDWP.VirtualMachine.InstanceCounts.process(vm, classes).counts;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public void dispose() {
        shutdown = true;
        try {
            JDWP.VirtualMachine.Dispose.process(vm);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        target.stopListening();
    }

    public void exit(int exitCode) {
        shutdown = true;
        try {
            JDWP.VirtualMachine.Exit.process(vm, exitCode);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        target.stopListening();
    }

    public void loadRuleIndex(RuleIndexData index) {
        try {
            JDWP.VirtualMachine.LoadRuleIndex.process(vm, index);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    public Process process() {
        return process;
    }

    private JDWP.VirtualMachine.Version versionInfo() {
       try {
           if (versionInfo == null) {
               versionInfo = JDWP.VirtualMachine.Version.process(vm);
           }
           return versionInfo;
       } catch (JDWPException exc) {
           throw exc.toJDIException();
       }
    }

    public String version() {
        return versionInfo().vmVersion;
    }

    public String name() {
        return versionInfo().vmName;
    }

    public boolean canWatchFieldModification() {
        return capabilities().canWatchFieldModification;
    }

    public boolean canWatchFieldAccess() {
        return capabilities().canWatchFieldAccess;
    }

    public boolean canGetBytecodes() {
        return capabilities().canGetBytecodes;
    }

    public boolean canGetSyntheticAttribute() {
        return capabilities().canGetSyntheticAttribute;
    }

    public boolean canGetOwnedMonitorInfo() {
        return capabilities().canGetOwnedMonitorInfo;
    }

    public boolean canGetCurrentContendedMonitor() {
        return capabilities().canGetCurrentContendedMonitor;
    }

    public boolean canGetMonitorInfo() {
        return capabilities().canGetMonitorInfo;
    }

    private boolean hasNewCapabilities() {
        return versionInfo().jdwpMajor > 1 || versionInfo().jdwpMinor >= 4;
    }

    boolean canGet1_5LanguageFeatures() {
        return versionInfo().jdwpMajor > 1 || versionInfo().jdwpMinor >= 5;
    }

    public boolean canUseInstanceFilters() {
        return hasNewCapabilities() && capabilitiesNew().canUseInstanceFilters;
    }

    public boolean canRedefineClasses() {
        return hasNewCapabilities() && capabilitiesNew().canRedefineClasses;
    }

    @Deprecated(since="15")
    public boolean canAddMethod() {
        return hasNewCapabilities() && capabilitiesNew().canAddMethod;
    }

    @Deprecated(since="15")
    public boolean canUnrestrictedlyRedefineClasses() {
        return hasNewCapabilities() && capabilitiesNew().canUnrestrictedlyRedefineClasses;
    }

    public boolean canPopFrames() {
        return hasNewCapabilities() && capabilitiesNew().canPopFrames;
    }

    public boolean canGetMethodReturnValues() {
        return versionInfo().jdwpMajor > 1 || versionInfo().jdwpMinor >= 6;
    }

    public boolean canGetInstanceInfo() {
        if (versionInfo().jdwpMajor > 1 || versionInfo().jdwpMinor >= 6) {
            return hasNewCapabilities() && capabilitiesNew().canGetInstanceInfo;
        } else {
            return false;
        }
    }

    public boolean canUseSourceNameFilters() {
        return versionInfo().jdwpMajor > 1 || versionInfo().jdwpMinor >= 6;
    }

    public boolean canForceEarlyReturn() {
        return hasNewCapabilities() && capabilitiesNew().canForceEarlyReturn;
    }

    public boolean canBeModified() {
        return true;
    }

    public boolean canGetSourceDebugExtension() {
        return hasNewCapabilities() && capabilitiesNew().canGetSourceDebugExtension;
    }

    public boolean canGetClassFileVersion() {
        return versionInfo().jdwpMajor > 1 || versionInfo().jdwpMinor >= 6;
    }

    public boolean canGetConstantPool() {
        return hasNewCapabilities() && capabilitiesNew().canGetConstantPool;
    }

    public boolean canRequestVMDeathEvent() {
        return hasNewCapabilities() && capabilitiesNew().canRequestVMDeathEvent;
    }

    public boolean canRequestMonitorEvents() {
        return hasNewCapabilities() && capabilitiesNew().canRequestMonitorEvents;
    }

    public boolean canGetMonitorFrameInfo() {
        return hasNewCapabilities() && capabilitiesNew().canGetMonitorFrameInfo;
    }

    public boolean canGetModuleInfo() {
        return versionInfo().jdwpMajor >= 9;
    }

    boolean mayCreateVirtualThreads() {
        return versionInfo().jdwpMajor >= 19;
    }

    private synchronized ReferenceTypeImpl addReferenceType(long id, int tag, String signature) {
        ReferenceTypeImpl existing = (ReferenceTypeImpl) typesByID.get(id);
        if (existing != null) {
            return existing;
        }
        ReferenceTypeImpl type = switch (tag) {
            case JDWP.TypeTag.CLASS -> new ClassTypeImpl(vm, id);
            case JDWP.TypeTag.INTERFACE -> new InterfaceTypeImpl(vm, id);
            case JDWP.TypeTag.ARRAY -> new ArrayTypeImpl(vm, id);
            default -> throw new InternalException("Invalid reference type tag");
        };

        if (signature == null && retrievedAllTypes) {
            return type;
        }

        typesByID.put(id, type);
        typesBySignature.add(type);

        return type;
    }

    synchronized void removeReferenceType(String signature) {
        Iterator<ReferenceType> iter = typesBySignature.iterator();
        int matches = 0;
        while (iter.hasNext()) {
            ReferenceTypeImpl type = (ReferenceTypeImpl)iter.next();
            int comp = signature.compareTo(type.signature());
            if (comp == 0) {
                matches++;
                iter.remove();
                typesByID.remove(type.ref());
            }
        }

        if (matches > 1) {
            retrieveClassesBySignature(signature);
        }
    }

    private synchronized List<ReferenceType> findReferenceTypes(String signature) {
        Iterator<ReferenceType> iter = typesBySignature.iterator();
        List<ReferenceType> list = new ArrayList<>();
        while (iter.hasNext()) {
            ReferenceTypeImpl type = (ReferenceTypeImpl)iter.next();
            int comp = signature.compareTo(type.signature());
            if (comp == 0) {
                list.add(type);
            }
        }
        return list;
    }

    ReferenceTypeImpl referenceType(long ref, byte tag) {
        return referenceType(ref, tag, null);
    }

    ClassTypeImpl classType(long ref) {
        return (ClassTypeImpl) referenceType(ref, JDWP.TypeTag.CLASS, null);
    }

    InterfaceTypeImpl interfaceType(long ref) {
        return (InterfaceTypeImpl) referenceType(ref, JDWP.TypeTag.INTERFACE, null);
    }

    ArrayTypeImpl arrayType(long ref) {
        return (ArrayTypeImpl) referenceType(ref, JDWP.TypeTag.ARRAY, null);
    }

    ReferenceTypeImpl referenceType(long id, int tag, String signature) {
        if (id == 0) {
            return null;
        }
        ReferenceTypeImpl retType = (ReferenceTypeImpl) typesByID.get(id);
        if (retType == null) {
            retType = addReferenceType(id, tag, signature);
        }
        if (signature != null) {
            retType.setSignature(signature);
        }
        return retType;
    }

    private JDWP.VirtualMachine.Capabilities capabilities() {
        if (capabilities == null) {
            try {
                capabilities = JDWP.VirtualMachine.Capabilities.process(vm);
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return capabilities;
    }

    private JDWP.VirtualMachine.CapabilitiesNew capabilitiesNew() {
        if (capabilitiesNew == null) {
            try {
                capabilitiesNew = JDWP.VirtualMachine.CapabilitiesNew.process(vm);
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return capabilitiesNew;
    }

    private synchronized ModuleReference addModule(long id) {
        if (modulesByID == null) {
            modulesByID = new HashMap<>(77);
        }
        ModuleReference module = new ModuleReferenceImpl(vm, id);
        modulesByID.put(id, module);
        return module;
    }

    ModuleReference getModule(long id) {
        if (id == 0) {
            return null;
        } else {
            ModuleReference module = null;
            synchronized (this) {
                if (modulesByID != null) {
                    module = modulesByID.get(id);
                }
                if (module == null) {
                    module = addModule(id);
                }
            }
            return module;
        }
    }

    private synchronized List<ModuleReference> retrieveAllModules() {
        List<ModuleReferenceImpl> reqModules;
        try {
            reqModules = JDWP.VirtualMachine.AllModules.process(vm).modules;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }

        List<ModuleReference> modules = new ArrayList<>(reqModules.size());
        for (ModuleReferenceImpl reqModule : reqModules) {
            modules.add(getModule(reqModule.ref()));
        }
        return modules;
    }

    private List<ReferenceType> retrieveClassesBySignature(String signature) {
        try {
            return JDWP.VirtualMachine.ClassesBySignature.process(vm, signature).classes;
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    private void retrieveAllClasses() {
        try {
            if (vm.canGet1_5LanguageFeatures()) {
                JDWP.VirtualMachine.AllClassesWithGeneric.process(vm);
            } else {
                JDWP.VirtualMachine.AllClasses.process(vm);
            }
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
        retrievedAllTypes = true;
    }

    void sendToTarget(Packet packet) {
        target.send(packet);
    }

    void waitForTargetReply(Packet packet) {
        target.waitForReply(packet);
        processBatchedDisposes();
    }

    Type findBootType(String signature) throws ClassNotLoadedException {
        List<ReferenceType> types = retrieveClassesBySignature(signature);
        for (ReferenceType type : types) {
            if (type.classLoader() == null) {
                return type;
            }
        }
        JNITypeParser parser = new JNITypeParser(signature);
        throw new ClassNotLoadedException(parser.typeName(), "Type " + parser.typeName() + " not loaded");
    }

    PrimitiveType primitiveTypeMirror(byte tag) {
        return switch (tag) {
            case JDWP.Tag.BOOLEAN -> theBooleanType;
            case JDWP.Tag.BYTE -> theByteType;
            case JDWP.Tag.CHAR -> theCharType;
            case JDWP.Tag.SHORT -> theShortType;
            case JDWP.Tag.INT -> theIntegerType;
            case JDWP.Tag.LONG -> theLongType;
            case JDWP.Tag.FLOAT -> theFloatType;
            case JDWP.Tag.DOUBLE -> theDoubleType;
            default -> throw new IllegalArgumentException("Unrecognized primitive tag " + tag);
        };
    }

    private void processBatchedDisposes() {
        if (shutdown) {
            return;
        }

        List<JDWP.VirtualMachine.DisposeObjects.Request> requests = null;
        synchronized (batchedDisposeRequests) {
            int size = batchedDisposeRequests.size();
            if (size >= DISPOSE_THRESHOLD) {
                requests = new ArrayList<>(size);
                for (SoftObjectReference ref : batchedDisposeRequests) {
                    requests.add(new JDWP.VirtualMachine.DisposeObjects.Request(new ObjectReferenceImpl(this, ref.key()), ref.count()));
                }
                batchedDisposeRequests.clear();
            }
        }
        if (requests != null) {
            try {
                JDWP.VirtualMachine.DisposeObjects.process(vm, requests);
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
    }

    private void batchForDispose(SoftObjectReference ref) {
        batchedDisposeRequests.add(ref);
    }

    private void processQueue() {
        Reference<?> ref;
        boolean found = false;
        while ((ref = referenceQueue.poll()) != null) {
            SoftObjectReference softRef = (SoftObjectReference) ref;
            removeObjectMirror(softRef);
            batchForDispose(softRef);
            found = true;
        }

        if (found) {
            processBatchedDisposes();
        }
    }

    synchronized ObjectReferenceImpl objectMirror(long id, int tag) {
        processQueue();

        if (id == 0) {
            return null;
        }
        ObjectReferenceImpl object = null;
        Long key = id;

        SoftObjectReference ref = objectsByID.get(key);
        if (ref != null) {
            object = ref.object();
        }

        if (object == null) {
            switch (tag) {
                case JDWP.Tag.OBJECT:
                    object = new ObjectReferenceImpl(vm, id);
                    break;
                case JDWP.Tag.STRING:
                    object = new StringReferenceImpl(vm, id);
                    break;
                case JDWP.Tag.ARRAY:
                    object = new ArrayReferenceImpl(vm, id);
                    break;
                case JDWP.Tag.THREAD:
                    ThreadReferenceImpl thread = new ThreadReferenceImpl(vm, id);
                    thread.addListener(this);
                    object = thread;
                    break;
                case JDWP.Tag.THREAD_GROUP:
                    object = new ThreadGroupReferenceImpl(vm, id);
                    break;
                case JDWP.Tag.CLASS_LOADER:
                    object = new ClassLoaderReferenceImpl(vm, id);
                    break;
                case JDWP.Tag.CLASS_OBJECT:
                    object = new ClassObjectReferenceImpl(vm, id);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid object tag: " + tag);
            }
            ref = new SoftObjectReference(key, object, referenceQueue);
            objectsByID.put(key, ref);
        } else {
            ref.incrementCount();
        }

        return object;
    }

    private synchronized void removeObjectMirror(SoftObjectReference ref) {
        objectsByID.remove(ref.key());
    }

    ObjectReferenceImpl objectMirror(long id) {
        return objectMirror(id, JDWP.Tag.OBJECT);
    }

    StringReferenceImpl stringMirror(long id) {
        return (StringReferenceImpl) objectMirror(id, JDWP.Tag.STRING);
    }

    ArrayReferenceImpl arrayMirror(long id) {
       return (ArrayReferenceImpl) objectMirror(id, JDWP.Tag.ARRAY);
    }

    ThreadReferenceImpl threadMirror(long id) {
        return (ThreadReferenceImpl) objectMirror(id, JDWP.Tag.THREAD);
    }

    ThreadGroupReferenceImpl threadGroupMirror(long id) {
        return (ThreadGroupReferenceImpl) objectMirror(id, JDWP.Tag.THREAD_GROUP);
    }

    ClassLoaderReferenceImpl classLoaderMirror(long id) {
        return (ClassLoaderReferenceImpl) objectMirror(id, JDWP.Tag.CLASS_LOADER);
    }

    ClassObjectReferenceImpl classObjectMirror(long id) {
        return (ClassObjectReferenceImpl) objectMirror(id, JDWP.Tag.CLASS_OBJECT);
    }

    ModuleReferenceImpl moduleMirror(long id) {
        return (ModuleReferenceImpl) getModule(id);
    }

    private JDWP.VirtualMachine.ClassPaths getClasspath() {
        if (pathInfo == null) {
            try {
                pathInfo = JDWP.VirtualMachine.ClassPaths.process(vm);
            } catch (JDWPException exc) {
                throw exc.toJDIException();
            }
        }
        return pathInfo;
    }

   public List<String> classPath() {
       return getClasspath().classpaths;
   }

   public List<String> bootClassPath() {
       return getClasspath().bootclasspaths;
   }

   public String baseDirectory() {
       return getClasspath().baseDir;
   }

   public String getDefaultStratum() {
        return defaultStratum;
    }

    ThreadGroup threadGroupForJDI() {
        return threadGroupForJDI;
    }

    public void setDefaultStratum(String stratum) {
        defaultStratum = stratum;
        if (stratum == null) {
            stratum = "";
        }
        try {
            JDWP.VirtualMachine.SetDefaultStratum.process(vm, stratum);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

   private static class SoftObjectReference extends SoftReference<ObjectReferenceImpl> {
       final Long key;
       int count;

       SoftObjectReference(Long key, ObjectReferenceImpl mirror, ReferenceQueue<ObjectReferenceImpl> queue) {
           super(mirror, queue);
           this.count = 1;
           this.key = key;
       }

       int count() {
           return count;
       }

       void incrementCount() {
           count++;
       }

       Long key() {
           return key;
       }

       ObjectReferenceImpl object() {
           return get();
       }
   }

    // SCANNER ADDED
    @Override
    public void setStackTraceFilters(String[] filters) {
        try {
            JDWP.VirtualMachine.StackTraceFilters.process(vm, filters);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    @Override
    public void setThreadNameFilters(String[] filters) {
        try {
            JDWP.VirtualMachine.ThreadNameFilters.process(vm, filters);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }

    @Override
    public void setSourceNameFilters(String[] filters) {
        try {
            JDWP.VirtualMachine.SourceNameFilters.process(vm, filters);
        } catch (JDWPException exc) {
            throw exc.toJDIException();
        }
    }
}
