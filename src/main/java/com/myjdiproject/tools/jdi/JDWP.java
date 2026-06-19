package com.myjdiproject.tools.jdi;

import com.myjdiproject.jdi.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

class JDWP {
    static class VirtualMachine {
        static final int COMMAND_SET = 1;
        private VirtualMachine() {}

        static class Version {
            static final int COMMAND = 1;

            static Version process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static Version waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Version(ps);
            }

            final String description;
            final int jdwpMajor;
            final int jdwpMinor;
            final String vmVersion;
            final String vmName;

            private Version(PacketStream ps) {
                description = ps.readString();
                jdwpMajor = ps.readInt();
                jdwpMinor = ps.readInt();
                vmVersion = ps.readString();
                vmName = ps.readString();
            }
        }

        static class ClassesBySignature {
            static final int COMMAND = 2;

            static ClassesBySignature process(VirtualMachineImpl vm, String signature) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, signature);
                ps.waitForReply();
                return new ClassesBySignature(ps, vm, signature);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, String signature) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeString(signature);
                ps.send();
                return ps;
            }

            final List<com.myjdiproject.jdi.ReferenceType> classes;

            private ClassesBySignature(PacketStream ps, VirtualMachineImpl vm, String signature) {
                int classesCount = ps.readInt();
                classes = new ArrayList<>(classesCount);
                for (int i = 0; i < classesCount; i++) {
                    byte refTypeTag = ps.readByte();
                    long typeID = ps.readClassRef();
                    int status = ps.readInt();
                    ReferenceTypeImpl type = vm.referenceType(typeID, refTypeTag, signature);
                    type.setStatus(status);
                    classes.add(type);
                }
            }
        }

        static class AllClasses {
            static final int COMMAND = 3;

            static AllClasses process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                ps.waitForReply();
                return new AllClasses(ps, vm);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            final List<com.myjdiproject.jdi.ReferenceType> classes;

            private AllClasses(PacketStream ps, VirtualMachineImpl vm) {
                int classesCount = ps.readInt();
                classes = new ArrayList<>(classesCount);
                for (int i = 0; i < classesCount; i++) {
                    byte refTypeTag = ps.readByte();
                    long typeID = ps.readClassRef();
                    String signature = ps.readString();
                    int status = ps.readInt();

                    ReferenceTypeImpl refType = vm.referenceType(typeID, refTypeTag, signature);
                    refType.setStatus(status);
                    classes.add(refType);
                }
            }
        }

        static class AllThreads {
            static final int COMMAND = 4;

            static AllThreads process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static AllThreads waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new AllThreads(ps);
            }

            final List<com.myjdiproject.jdi.ThreadReference> threads;

            private AllThreads(PacketStream ps) {
                int threadsCount = ps.readInt();
                threads = new ArrayList<>(threadsCount);
                for (int i = 0; i < threadsCount; i++) {
                    threads.add(ps.readThreadReference());
                }
            }
        }

        static class TopLevelThreadGroups {
            static final int COMMAND = 5;

            static TopLevelThreadGroups process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static TopLevelThreadGroups waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new TopLevelThreadGroups(ps);
            }

            final List<com.myjdiproject.jdi.ThreadGroupReference> groups;

            private TopLevelThreadGroups(PacketStream ps) {
                int groupsCount = ps.readInt();
                groups = new ArrayList<>(groupsCount);
                for (int i = 0; i < groupsCount; i++) {
                    groups.add(ps.readThreadGroupReference());
                }
            }
        }

        static class Dispose {
            static final int COMMAND = 6;

            static void process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class IDSizes {
            static final int COMMAND = 7;

            static IDSizes process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static IDSizes waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new IDSizes(ps);
            }

            final int fieldIDSize;
            final int methodIDSize;
            final int objectIDSize;
            final int referenceTypeIDSize;
            final int frameIDSize;

            private IDSizes(PacketStream ps) {
                fieldIDSize = ps.readInt();
                methodIDSize = ps.readInt();
                objectIDSize = ps.readInt();
                referenceTypeIDSize = ps.readInt();
                frameIDSize = ps.readInt();
            }
        }

        static class Suspend {
            static final int COMMAND = 8;

            static void process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class Resume {
            static final int COMMAND = 9;

            static void process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class Exit {
            static final int COMMAND = 10;

            static void process(VirtualMachineImpl vm, int exitCode) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, exitCode);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, int exitCode) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeInt(exitCode);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class CreateString {
            static final int COMMAND = 11;

            static CreateString process(VirtualMachineImpl vm, String utf) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, utf);
                return waitForReply(ps, utf);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, String utf) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeString(utf);
                ps.send();
                return ps;
            }

            static CreateString waitForReply(PacketStream ps, String utf) throws JDWPException {
                ps.waitForReply();
                return new CreateString(ps, utf);
            }

            final StringReferenceImpl stringObject;

            private CreateString(PacketStream ps, String utf) {
                stringObject = ps.readStringReference();
                if (stringObject != null) {
                    stringObject.setValue(utf);
                }
            }
        }

        static class Capabilities {
            static final int COMMAND = 12;

            static Capabilities process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static Capabilities waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Capabilities(ps);
            }

            final boolean canWatchFieldModification;
            final boolean canWatchFieldAccess;
            final boolean canGetBytecodes;
            final boolean canGetSyntheticAttribute;
            final boolean canGetOwnedMonitorInfo;
            final boolean canGetCurrentContendedMonitor;
            final boolean canGetMonitorInfo;

            private Capabilities(PacketStream ps) {
                canWatchFieldModification = ps.readBoolean();
                canWatchFieldAccess = ps.readBoolean();
                canGetBytecodes = ps.readBoolean();
                canGetSyntheticAttribute = ps.readBoolean();
                canGetOwnedMonitorInfo = ps.readBoolean();
                canGetCurrentContendedMonitor = ps.readBoolean();
                canGetMonitorInfo = ps.readBoolean();
            }
        }

        static class ClassPaths {
            static final int COMMAND = 13;

            static ClassPaths process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static ClassPaths waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ClassPaths(ps);
            }

            final String baseDir;
            final List<String> classpaths;
            final List<String> bootclasspaths;

            private ClassPaths(PacketStream ps) {
                baseDir = ps.readString();
                int classpathsCount = ps.readInt();
                classpaths = new ArrayList<>(classpathsCount);
                for (int i = 0; i < classpathsCount; i++) {
                    classpaths.add(ps.readString());
                }
                int bootclasspathsCount = ps.readInt();
                bootclasspaths = new ArrayList<>(bootclasspathsCount);
                for (int i = 0; i < bootclasspathsCount; i++) {
                    bootclasspaths.add(ps.readString());
                }
            }
        }

        static class DisposeObjects {
            static final int COMMAND = 14;

            record Request(ObjectReferenceImpl object, int refCnt) {
                private void write(PacketStream ps) {
                    ps.writeObjectRef(object.ref());
                    ps.writeInt(refCnt);
                }
            }

            static void process(VirtualMachineImpl vm, List<Request> requests) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, requests);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, List<Request> requests) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeInt(requests.size());
                for (Request request : requests) {
                    request.write(ps);
                }
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class HoldEvents {
            static final int COMMAND = 15;

            static void process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class ReleaseEvents {
            static final int COMMAND = 16;

            static void process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class CapabilitiesNew {
            static final int COMMAND = 17;

            static CapabilitiesNew process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static CapabilitiesNew waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new CapabilitiesNew(ps);
            }

            final boolean canWatchFieldModification;
            final boolean canWatchFieldAccess;
            final boolean canGetBytecodes;
            final boolean canGetSyntheticAttribute;
            final boolean canGetOwnedMonitorInfo;
            final boolean canGetCurrentContendedMonitor;
            final boolean canGetMonitorInfo;
            final boolean canRedefineClasses;
            final boolean canAddMethod;
            final boolean canUnrestrictedlyRedefineClasses;
            final boolean canPopFrames;
            final boolean canUseInstanceFilters;
            final boolean canGetSourceDebugExtension;
            final boolean canRequestVMDeathEvent;
            final boolean canSetDefaultStratum;
            final boolean canGetInstanceInfo;
            final boolean canRequestMonitorEvents;
            final boolean canGetMonitorFrameInfo;
            final boolean canUseSourceNameFilters;
            final boolean canGetConstantPool;
            final boolean canForceEarlyReturn;
            final boolean reserved22;
            final boolean reserved23;
            final boolean reserved24;
            final boolean reserved25;
            final boolean reserved26;
            final boolean reserved27;
            final boolean reserved28;
            final boolean reserved29;
            final boolean reserved30;
            final boolean reserved31;
            final boolean reserved32;

            private CapabilitiesNew(PacketStream ps) {
                canWatchFieldModification = ps.readBoolean();
                canWatchFieldAccess = ps.readBoolean();
                canGetBytecodes = ps.readBoolean();
                canGetSyntheticAttribute = ps.readBoolean();
                canGetOwnedMonitorInfo = ps.readBoolean();
                canGetCurrentContendedMonitor = ps.readBoolean();
                canGetMonitorInfo = ps.readBoolean();
                canRedefineClasses = ps.readBoolean();
                canAddMethod = ps.readBoolean();
                canUnrestrictedlyRedefineClasses = ps.readBoolean();
                canPopFrames = ps.readBoolean();
                canUseInstanceFilters = ps.readBoolean();
                canGetSourceDebugExtension = ps.readBoolean();
                canRequestVMDeathEvent = ps.readBoolean();
                canSetDefaultStratum = ps.readBoolean();
                canGetInstanceInfo = ps.readBoolean();
                canRequestMonitorEvents = ps.readBoolean();
                canGetMonitorFrameInfo = ps.readBoolean();
                canUseSourceNameFilters = ps.readBoolean();
                canGetConstantPool = ps.readBoolean();
                canForceEarlyReturn = ps.readBoolean();
                reserved22 = ps.readBoolean();
                reserved23 = ps.readBoolean();
                reserved24 = ps.readBoolean();
                reserved25 = ps.readBoolean();
                reserved26 = ps.readBoolean();
                reserved27 = ps.readBoolean();
                reserved28 = ps.readBoolean();
                reserved29 = ps.readBoolean();
                reserved30 = ps.readBoolean();
                reserved31 = ps.readBoolean();
                reserved32 = ps.readBoolean();
            }
        }

        static class RedefineClasses {
            static final int COMMAND = 18;

            static void process(VirtualMachineImpl vm, Map<? extends com.myjdiproject.jdi.ReferenceType, byte[]> classToBytes) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, classToBytes);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, Map<? extends com.myjdiproject.jdi.ReferenceType, byte[]> classToBytes) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeInt(classToBytes.size());
                for (Map.Entry<? extends com.myjdiproject.jdi.ReferenceType, byte[]> entry : classToBytes.entrySet()) {
                    ReferenceTypeImpl refType = (ReferenceTypeImpl) entry.getKey();
                    byte[] classFile = entry.getValue();
                    vm.validateMirror(refType);

                    ps.writeClassRef(refType.ref());
                    ps.writeByteArray(classFile);
                }
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class SetDefaultStratum {
            static final int COMMAND = 19;

            static void process(VirtualMachineImpl vm, String stratumID) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, stratumID);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, String stratumID) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeString(stratumID);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class AllClassesWithGeneric {
            static final int COMMAND = 20;

            static AllClassesWithGeneric process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                ps.waitForReply();
                return new AllClassesWithGeneric(ps, vm);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            final List<com.myjdiproject.jdi.ReferenceType> classes;

            private AllClassesWithGeneric(PacketStream ps, VirtualMachineImpl vm) {
                int classesCount = ps.readInt();
                classes = new ArrayList<>(classesCount);
                for (int i = 0; i < classesCount; i++) {
                    byte refTypeTag = ps.readByte();
                    long typeID = ps.readClassRef();
                    String signature = ps.readString();
                    String genericSignature = ps.readString();
                    int status = ps.readInt();

                    ReferenceTypeImpl type = vm.referenceType(typeID, refTypeTag, signature);
                    type.setGenericSignature(genericSignature);
                    type.setStatus(status);
                    classes.add(type);
                }
            }
        }

        static class InstanceCounts {
            static final int COMMAND = 21;

            static InstanceCounts process(VirtualMachineImpl vm, List<? extends com.myjdiproject.jdi.ReferenceType> refTypesCount) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refTypesCount);
                ps.waitForReply();
                return new InstanceCounts(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, List<? extends com.myjdiproject.jdi.ReferenceType> refTypesCount) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeInt(refTypesCount.size());
                for (com.myjdiproject.jdi.ReferenceType referenceType : refTypesCount) {
                    ps.writeClassRef(referenceType.ref());
                }
                ps.send();
                return ps;
            }

            final long[] counts;

            private InstanceCounts(PacketStream ps) {
                int countsCount = ps.readInt();
                counts = new long[countsCount];
                for (int i = 0; i < countsCount; i++) {
                    counts[i] = ps.readLong();
                }
            }
        }

        static class AllModules {
            static final int COMMAND = 22;

            static AllModules process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static AllModules waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new AllModules(ps);
            }

            final List<ModuleReferenceImpl> modules;

            private AllModules(PacketStream ps) {
                int modulesCount = ps.readInt();
                modules = new ArrayList<>(modulesCount);
                for (int i = 0; i < modulesCount; i++) {
                    modules.add(ps.readModule());
                }
            }
        }

        // SCANNER ADDED
        static class StackTraceFilters {
            static final int COMMAND = 23;

            static void process(VirtualMachineImpl vm, String[] filters) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, filters);
                ps.waitForReply();
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, String[] filters) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeInt(filters.length);
                for (String filter : filters) {
                    ps.writeString(filter);
                }
                ps.send();
                return ps;
            }
        }

        static class ThreadNameFilters {
            static final int COMMAND = 24;

            static void process(VirtualMachineImpl vm, String[] filters) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, filters);
                ps.waitForReply();
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, String[] filters) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeInt(filters.length);
                for (String filter : filters) {
                    ps.writeString(filter);
                }
                ps.send();
                return ps;
            }
        }

        static class SourceNameFilters {
            static final int COMMAND = 25;

            static void process(VirtualMachineImpl vm, String[] filters) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, filters);
                ps.waitForReply();
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, String[] filters) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeInt(filters.length);
                for (String filter : filters) {
                    ps.writeString(filter);
                }
                ps.send();
                return ps;
            }
        }

        static class LoadRuleIndex {
            static final int COMMAND = 26;

            static final int MAGIC = 0x44415354;
            static final int VERSION = 1;

            static void process(VirtualMachineImpl vm, RuleIndexData index) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, index);
                ps.waitForReply();
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, RuleIndexData index) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeByteArray(serialize(index));
                ps.send();
                return ps;
            }

            private static byte[] serialize(RuleIndexData index) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                try {
                    out.writeInt(MAGIC);
                    out.writeByte(VERSION);

                    out.writeBoolean(index.classComplete);
                    writeStrings(out, index.classExact);
                    writeStrings(out, index.classPrefix);
                    writeStrings(out, index.classSubstrings);
                    writeStrings(out, index.methodNames);
                    writeStrings(out, index.fieldNames);
                    writeStrings(out, index.annotationDescriptors);

                    out.writeBoolean(index.argComplete);
                    out.writeInt(index.argRules.size());
                    for (RuleIndexData.ArgRule rule : index.argRules) {
                        writeString(out, rule.classKey);
                        writeString(out, rule.method);
                        out.writeInt(rule.predicates.size());
                        for (RuleIndexData.Predicate predicate : rule.predicates) {
                            writePredicate(out, predicate);
                        }
                    }
                    out.flush();
                } catch (IOException e) {
                    throw new InternalException("Failed to serialize the DAST runtime index: " + e.getMessage());
                }
                return bytes.toByteArray();
            }

            private static void writePredicate(DataOutputStream out, RuleIndexData.Predicate predicate)
                    throws IOException {
                writeString(out, predicate.position);
                int flags = 0;
                if (predicate.regex) {
                    flags |= 1;
                }
                if (predicate.number) {
                    flags |= 2;
                }
                if (predicate.caseSensitive) {
                    flags |= 4;
                }
                if (predicate.nullMatches) {
                    flags |= 8;
                }
                out.writeByte(flags);
                writeString(out, encodeArities(predicate.arities));
                writeStrings(out, predicate.literals);
            }

            private static void writeStrings(DataOutputStream out, List<String> values) throws IOException {
                out.writeInt(values.size());
                for (String value : values) {
                    writeString(out, value);
                }
            }

            private static void writeString(DataOutputStream out, String value) throws IOException {
                byte[] utf8 = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeInt(utf8.length);
                out.write(utf8);
            }

            private static String encodeArities(Set<Integer> arities) {
                if (arities == null || arities.isEmpty()) {
                    return "*";
                }
                List<Integer> sorted = new ArrayList<>(new TreeSet<>(arities));
                StringBuilder builder = new StringBuilder();
                int i = 0;
                while (i < sorted.size()) {
                    int lo = sorted.get(i);
                    int hi = lo;
                    while (i + 1 < sorted.size() && sorted.get(i + 1) == hi + 1) {
                        hi = sorted.get(++i);
                    }
                    if (builder.length() != 0) {
                        builder.append(',');
                    }
                    builder.append(lo);
                    if (hi != lo) {
                        builder.append('-').append(hi);
                    }
                    i++;
                }
                return builder.toString();
            }
        }
    }

    static class ReferenceType {
        static final int COMMAND_SET = 2;

        private ReferenceType() {}

        static class Signature {
            static final int COMMAND = 1;

            static Signature process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static Signature waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Signature(ps);
            }

            final String signature;

            private Signature(PacketStream ps) {
                signature = ps.readString();
            }
        }

        static class ClassLoader {
            static final int COMMAND = 2;

            static ClassLoader process(VirtualMachineImpl vm, com.myjdiproject.jdi.ReferenceType refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, com.myjdiproject.jdi.ReferenceType refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static ClassLoader waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ClassLoader(ps);
            }

            final ClassLoaderReferenceImpl classLoader;

            private ClassLoader(PacketStream ps) {
                classLoader = ps.readClassLoaderReference();
            }
        }

        static class Modifiers {
            static final int COMMAND = 3;

            static Modifiers process(VirtualMachineImpl vm, com.myjdiproject.jdi.ReferenceType refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, com.myjdiproject.jdi.ReferenceType refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static Modifiers waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Modifiers(ps);
            }

            final int modBits;

            private Modifiers(PacketStream ps) {
                modBits = ps.readInt();
            }
        }

        static class Fields {
            static final int COMMAND = 4;

            static Fields process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                ps.waitForReply();
                return new Fields(ps, vm, refType);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            final List<Field> fields;

            private Fields(PacketStream ps, VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                int declaredCount = ps.readInt();
                fields = new ArrayList<>(declaredCount);
                for (int i = 0; i < declaredCount; i++) {
                    long fieldID = ps.readFieldRef();
                    String name = ps.readString();
                    String signature = ps.readString();
                    int modBits = ps.readInt();
                    fields.add(new FieldImpl(vm, refType, fieldID, name, signature, null, modBits));
                }
            }
        }

        static class Methods {
            static final int COMMAND = 5;

            static Methods process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                ps.waitForReply();
                return new Methods(ps, vm, refType);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            final List<com.myjdiproject.jdi.Method> methods;

            private Methods(PacketStream ps, VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                int declaredCount = ps.readInt();
                methods = new ArrayList<>(declaredCount);
                for (int i = 0; i < declaredCount; i++) {
                    long methodID = ps.readMethodRef();
                    String name = ps.readString();
                    String signature = ps.readString();
                    int modBits = ps.readInt();
                    methods.add(MethodImpl.createMethodImpl(vm, refType, methodID, name, signature, null, modBits));
                }
            }
        }

        static class GetValues {
            static final int COMMAND = 6;

            static GetValues process(VirtualMachineImpl vm, ReferenceTypeImpl refType, List<? extends com.myjdiproject.jdi.Field> fields) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType, fields);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType, List<? extends com.myjdiproject.jdi.Field> fields) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.writeInt(fields.size());
                for (com.myjdiproject.jdi.Field field : fields) {
                    ps.writeFieldRef(field.ref());
                }
                ps.send();
                return ps;
            }

            static GetValues waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new GetValues(ps);
            }

            final List<Value> values;

            private GetValues(PacketStream ps) {
                int valuesCount = ps.readInt();
                values = new ArrayList<>(valuesCount);
                for (int i = 0; i < valuesCount; i++) {
                    values.add(ps.readValue());
                }
            }
        }

        static class SourceFile {
            static final int COMMAND = 7;

            static SourceFile process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static SourceFile waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new SourceFile(ps);
            }

            final String sourceFile;

            private SourceFile(PacketStream ps) {
                sourceFile = ps.readString();
            }
        }

        static class NestedTypes {
            static final int COMMAND = 8;

            static NestedTypes process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                ps.waitForReply();
                return new NestedTypes(ps, vm);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            final List<com.myjdiproject.jdi.ReferenceType> classes;

            private NestedTypes(PacketStream ps, VirtualMachineImpl vm) {
                int classesCount = ps.readInt();
                classes = new ArrayList<>(classesCount);
                for (int i = 0; i < classesCount; i++) {
                    byte refTypeTag = ps.readByte();
                    long typeID = ps.readClassRef();
                    classes.add(vm.referenceType(typeID, refTypeTag));
                }
            }
        }

        static class Status {
            static final int COMMAND = 9;

            static Status process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static Status waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Status(ps);
            }

            final int status;

            private Status(PacketStream ps) {
                status = ps.readInt();
            }
        }

        static class Interfaces {
            static final int COMMAND = 10;

            static Interfaces process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(vm, ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static Interfaces waitForReply(VirtualMachineImpl vm, PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Interfaces(vm, ps);
            }

            final List<com.myjdiproject.jdi.InterfaceType> interfaces;

            private Interfaces(VirtualMachineImpl vm, PacketStream ps) {
                int interfacesCount = ps.readInt();
                interfaces = new ArrayList<>(interfacesCount);
                for (int i = 0; i < interfacesCount; i++) {
                    interfaces.add(vm.interfaceType(ps.readClassRef()));
                }
            }
        }

        static class ClassObject {
            static final int COMMAND = 11;

            static ClassObject process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static ClassObject waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ClassObject(ps);
            }

            final ClassObjectReferenceImpl classObject;

            private ClassObject(PacketStream ps) {
                classObject = ps.readClassObjectReference();
            }
        }

        static class SourceDebugExtension {
            static final int COMMAND = 12;

            static SourceDebugExtension process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static SourceDebugExtension waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new SourceDebugExtension(ps);
            }

            final String extension;

            private SourceDebugExtension(PacketStream ps) {
                extension = ps.readString();
            }
        }

        static class SignatureWithGeneric {
            static final int COMMAND = 13;

            static SignatureWithGeneric process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static SignatureWithGeneric waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new SignatureWithGeneric(ps);
            }

            final String signature;
            final String genericSignature;

            private SignatureWithGeneric(PacketStream ps) {
                signature = ps.readString();
                genericSignature = ps.readString();
            }
        }

        static class FieldsWithGeneric {
            static final int COMMAND = 14;

            static FieldsWithGeneric process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                ps.waitForReply();
                return new FieldsWithGeneric(ps, vm, refType);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            final List<Field> fields;

            private FieldsWithGeneric(PacketStream ps, VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                int fieldsCount = ps.readInt();
                fields = new ArrayList<>(fieldsCount);
                for (int i = 0; i < fieldsCount; i++) {
                    long fieldID = ps.readFieldRef();
                    String name = ps.readString();
                    String signature = ps.readString();
                    String genericSignature = ps.readString();
                    int modBits = ps.readInt();
                    fields.add(new FieldImpl(vm, refType, fieldID, name, signature, genericSignature, modBits));
                }
            }
        }

        static class MethodsWithGeneric {
            static final int COMMAND = 15;

            static MethodsWithGeneric process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                ps.waitForReply();
                return new MethodsWithGeneric(ps, vm, refType);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            final List<com.myjdiproject.jdi.Method> methods;

            private MethodsWithGeneric(PacketStream ps, VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                int declaredCount = ps.readInt();
                methods = new ArrayList<>(declaredCount);
                for (int i = 0; i < declaredCount; i++) {
                    long methodID = ps.readMethodRef();
                    String name = ps.readString();
                    String signature = ps.readString();
                    String genericSignature = ps.readString();
                    int modBits = ps.readInt();
                    methods.add(MethodImpl.createMethodImpl(vm, refType, methodID, name, signature, genericSignature, modBits));
                }
            }
        }

        static class Instances {
            static final int COMMAND = 16;

            static Instances process(VirtualMachineImpl vm, ReferenceTypeImpl refType, int maxInstances) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType, maxInstances);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType, int maxInstances) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.writeInt(maxInstances);
                ps.send();
                return ps;
            }

            static Instances waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Instances(ps);
            }

            final List<com.myjdiproject.jdi.ObjectReference> instances;

            private Instances(PacketStream ps) {
                int instancesCount = ps.readInt();
                instances = new ArrayList<>(instancesCount);
                for (int i = 0; i < instancesCount; i++) {
                    instances.add(ps.readTaggedObjectReference());
                }
            }
        }

        static class ClassFileVersion {
            static final int COMMAND = 17;

            static ClassFileVersion process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static ClassFileVersion waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ClassFileVersion(ps);
            }

            final int majorVersion;
            final int minorVersion;

            private ClassFileVersion(PacketStream ps) {
                majorVersion = ps.readInt();
                minorVersion = ps.readInt();
            }
        }

        static class ConstantPool {
            static final int COMMAND = 18;

            static ConstantPool process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static ConstantPool waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ConstantPool(ps);
            }

            final int count;
            final byte[] bytes;

            private ConstantPool(PacketStream ps) {
                count = ps.readInt();
                bytes = ps.readByteArray();
            }
        }

        static class Module {
            static final int COMMAND = 19;

            static Module process(VirtualMachineImpl vm, ReferenceTypeImpl refType) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.send();
                return ps;
            }

            static Module waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Module(ps);
            }

            final ModuleReferenceImpl module;

            private Module(PacketStream ps) {
                module = ps.readModule();
            }
        }
    }

    static class ClassType {
        static final int COMMAND_SET = 3;
        private ClassType() {}

        static class Superclass {
            static final int COMMAND = 1;

            static Superclass process(VirtualMachineImpl vm, ClassTypeImpl clazz) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, clazz);
                return waitForReply(vm, ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ClassTypeImpl clazz) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(clazz.ref());
                ps.send();
                return ps;
            }

            static Superclass waitForReply(VirtualMachineImpl vm, PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Superclass(vm, ps);
            }

            final ClassTypeImpl superclass;

            private Superclass(VirtualMachineImpl vm, PacketStream ps) {
                superclass = vm.classType(ps.readClassRef());
            }
        }

        static class SetValues {
            static final int COMMAND = 2;

            record FieldValue(long fieldID, ValueImpl value) {
                private void write(PacketStream ps) {
                    ps.writeFieldRef(fieldID);
                    ps.writeUntaggedValue(value);
                }
            }

            static void process(VirtualMachineImpl vm, ClassTypeImpl clazz, List<FieldValue> values) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, clazz, values);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ClassTypeImpl clazz, List<FieldValue> values) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(clazz.ref());
                ps.writeInt(values.size());
                for (FieldValue value : values) {
                    value.write(ps);
                }
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class InvokeMethod {
            static final int COMMAND = 3;

            static InvokeMethod process(VirtualMachineImpl vm, ClassTypeImpl clazz, ThreadReferenceImpl thread, long methodID, List<Value> arguments, int options) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, clazz, thread, methodID, arguments, options);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ClassTypeImpl clazz, ThreadReferenceImpl thread, long methodID, List<Value> arguments, int options) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(clazz.ref());
                ps.writeObjectRef(thread.ref());
                ps.writeMethodRef(methodID);
                ps.writeInt(arguments.size());
                for (Value argument : arguments) {
                    ps.writeValue(argument);
                }
                ps.writeInt(options);
                ps.send();
                return ps;
            }

            static InvokeMethod waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new InvokeMethod(ps);
            }

            final ValueImpl returnValue;
            final ObjectReferenceImpl exception;

            private InvokeMethod(PacketStream ps) {
                returnValue = ps.readValue();
                exception = ps.readTaggedObjectReference();
            }
        }

        static class NewInstance {
            static final int COMMAND = 4;

            static NewInstance process(VirtualMachineImpl vm, ClassTypeImpl clazz, ThreadReferenceImpl thread, long methodID, List<Value> arguments, int options) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, clazz, thread, methodID, arguments, options);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ClassTypeImpl clazz, ThreadReferenceImpl thread, long methodID, List<Value> arguments, int options) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(clazz.ref());
                ps.writeObjectRef(thread.ref());
                ps.writeMethodRef(methodID);
                ps.writeInt(arguments.size());
                for (Value argument : arguments) {
                    ps.writeValue(argument);
                }
                ps.writeInt(options);
                ps.send();
                return ps;
            }

            static NewInstance waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new NewInstance(ps);
            }

            final ObjectReferenceImpl newObject;
            final ObjectReferenceImpl exception;

            private NewInstance(PacketStream ps) {
                newObject = ps.readTaggedObjectReference();
                exception = ps.readTaggedObjectReference();
            }
        }
    }

    static class ArrayType {
        static final int COMMAND_SET = 4;

        private ArrayType() {}

        static class NewInstance {
            static final int COMMAND = 1;

            static NewInstance process(VirtualMachineImpl vm, ArrayTypeImpl arrType, int length) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, arrType, length);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ArrayTypeImpl arrType, int length) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(arrType.ref());
                ps.writeInt(length);
                ps.send();
                return ps;
            }

            static NewInstance waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new NewInstance(ps);
            }

            final ObjectReferenceImpl newArray;

            private NewInstance(PacketStream ps) {
                newArray = ps.readTaggedObjectReference();
            }
        }
    }

    static class InterfaceType {
        static final int COMMAND_SET = 5;

        private InterfaceType() {}

        static class InvokeMethod {
            static final int COMMAND = 1;

            static InvokeMethod process(VirtualMachineImpl vm, InterfaceTypeImpl clazz, ThreadReferenceImpl thread, long methodID, List<Value> arguments, int options) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, clazz, thread, methodID, arguments, options);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, InterfaceTypeImpl clazz, ThreadReferenceImpl thread, long methodID, List<Value> arguments, int options) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(clazz.ref());
                ps.writeObjectRef(thread.ref());
                ps.writeMethodRef(methodID);
                ps.writeInt(arguments.size());
                for (Value argument : arguments) {
                    ps.writeValue(argument);
                }
                ps.writeInt(options);
                ps.send();
                return ps;
            }

            static InvokeMethod waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new InvokeMethod(ps);
            }

            final ValueImpl returnValue;
            final ObjectReferenceImpl exception;

            private InvokeMethod(PacketStream ps) {
                returnValue = ps.readValue();
                exception = ps.readTaggedObjectReference();
            }
        }
    }

    static class Method {
        static final int COMMAND_SET = 6;
        private Method() {}

        static class LineTable {
            static final int COMMAND = 1;

            static LineTable process(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType, methodID);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.writeMethodRef(methodID);
                ps.send();
                return ps;
            }

            static LineTable waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new LineTable(ps);
            }

            static class LineInfo {
                final long lineCodeIndex;
                final int lineNumber;

                private LineInfo(PacketStream ps) {
                    lineCodeIndex = ps.readLong();
                    lineNumber = ps.readInt();
                }
            }

            final long start;
            final long end;
            final List<LineInfo> lines;

            private LineTable(PacketStream ps) {
                start = ps.readLong();
                end = ps.readLong();
                int linesCount = ps.readInt();
                lines = new ArrayList<>(linesCount);
                for (int i = 0; i < linesCount; i++) {
                    lines.add(new LineInfo(ps));
                }
            }
        }

        static class VariableTable {
            static final int COMMAND = 2;

            static VariableTable process(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType, methodID);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.writeMethodRef(methodID);
                ps.send();
                return ps;
            }

            static VariableTable waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new VariableTable(ps);
            }

            static class SlotInfo {
                final long codeIndex;
                final String name;
                final String signature;
                final int length;
                final int slot;

                private SlotInfo(PacketStream ps) {
                    codeIndex = ps.readLong();
                    name = ps.readString();
                    signature = ps.readString();
                    length = ps.readInt();
                    slot = ps.readInt();
                }
            }

            final int argCnt;
            final List<SlotInfo> slots;

            private VariableTable(PacketStream ps) {
                argCnt = ps.readInt();
                int slotsCount = ps.readInt();
                slots = new ArrayList<>(slotsCount);
                for (int i = 0; i < slotsCount; i++) {
                    slots.add(new SlotInfo(ps));
                }
            }
        }

        static class Bytecodes {
            static final int COMMAND = 3;

            static Bytecodes process(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType, methodID);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.writeMethodRef(methodID);
                ps.send();
                return ps;
            }

            static Bytecodes waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Bytecodes(ps);
            }

            final byte[] bytes;

            private Bytecodes(PacketStream ps) {
                bytes = ps.readByteArray();
            }
        }

        static class IsObsolete {
            static final int COMMAND = 4;

            static IsObsolete process(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType, methodID);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.writeMethodRef(methodID);
                ps.send();
                return ps;
            }

            static IsObsolete waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new IsObsolete(ps);
            }

            final boolean isObsolete;

            private IsObsolete(PacketStream ps) {
                isObsolete = ps.readBoolean();
            }
        }

        static class VariableTableWithGeneric {
            static final int COMMAND = 5;

            static VariableTableWithGeneric process(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType, methodID);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.writeMethodRef(methodID);
                ps.send();
                return ps;
            }

            static VariableTableWithGeneric waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new VariableTableWithGeneric(ps);
            }

            static class SlotInfo {
                final long codeIndex;
                final String name;
                final String signature;
                final String genericSignature;
                final int length;
                final int slot;

                private SlotInfo(PacketStream ps) {
                    codeIndex = ps.readLong();
                    name = ps.readString();
                    signature = ps.readString();
                    genericSignature = ps.readString();
                    length = ps.readInt();
                    slot = ps.readInt();
                }
            }

            final int argCnt;
            final List<SlotInfo> slots;

            private VariableTableWithGeneric(PacketStream ps) {
                argCnt = ps.readInt();
                int slotsCount = ps.readInt();
                slots = new ArrayList<>(slotsCount);
                for (int i = 0; i < slotsCount; i++) {
                    slots.add(new SlotInfo(ps));
                }
            }
        }

        static class AnnotationTypes {
            static final int COMMAND = 6;

            static AnnotationTypes process(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, refType, methodID);
                return waitForReply(ps, vm);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ReferenceTypeImpl refType, long methodID) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeClassRef(refType.ref());
                ps.writeMethodRef(methodID);
                ps.send();
                return ps;
            }

            static AnnotationTypes waitForReply(PacketStream ps, VirtualMachineImpl vm) throws JDWPException {
                ps.waitForReply();
                return new AnnotationTypes(ps, vm);
            }

            final List<com.myjdiproject.jdi.ReferenceType> types;

            private AnnotationTypes(PacketStream ps, VirtualMachineImpl vm) {
                int typesCount = ps.readInt();
                types = new ArrayList<>(typesCount);
                for (int i = 0; i < typesCount; i++) {
                    byte refTypeTag = ps.readByte();
                    long typeID = ps.readClassRef();
                    int status = ps.readInt();

                    ReferenceTypeImpl type = vm.referenceType(typeID, refTypeTag);
                    type.setStatus(status);
                    types.add(type);
                }
            }
        }
    }

    static class ObjectReference {
        static final int COMMAND_SET = 9;

        private ObjectReference() {}

        static class ReferenceType {
            static final int COMMAND = 1;

            static ReferenceType process(VirtualMachineImpl vm, ObjectReferenceImpl object) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object);
                return waitForReply(ps, vm);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.send();
                return ps;
            }

            static ReferenceType waitForReply(PacketStream ps, VirtualMachineImpl vm) throws JDWPException {
                ps.waitForReply();
                return new ReferenceType(ps, vm);
            }

            final ReferenceTypeImpl referenceType;

            private ReferenceType(PacketStream ps, VirtualMachineImpl vm) {
                byte refTypeTag = ps.readByte();
                long typeID = ps.readClassRef();
                referenceType = vm.referenceType(typeID, refTypeTag);
            }
        }

        static class GetValues {
            static final int COMMAND = 2;

            static GetValues process(VirtualMachineImpl vm, ObjectReferenceImpl object, List<com.myjdiproject.jdi.Field> fields) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object, fields);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object, List<com.myjdiproject.jdi.Field> fields) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.writeInt(fields.size());
                for (com.myjdiproject.jdi.Field field : fields) {
                    ps.writeFieldRef(((FieldImpl) field).ref());
                }
                ps.send();
                return ps;
            }

            static GetValues waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new GetValues(ps);
            }

            final List<Value> values;

            private GetValues(PacketStream ps) {
                int valuesCount = ps.readInt();
                values = new ArrayList<>(valuesCount);
                for (int i = 0; i < valuesCount; i++) {
                    values.add(ps.readValue());
                }
            }
        }

        static class SetValues {
            static final int COMMAND = 3;

            record FieldValue(long fieldID, ValueImpl value) {
                private void write(PacketStream ps) {
                    ps.writeFieldRef(fieldID);
                    ps.writeUntaggedValue(value);
                }
            }

            static void process(VirtualMachineImpl vm, ObjectReferenceImpl object, List<FieldValue> values) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object, values);
                ps.waitForReply();
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object, List<FieldValue> values) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.writeInt(values.size());
                for (FieldValue value : values) {
                    value.write(ps);
                }
                ps.send();
                return ps;
            }
        }

        static class MonitorInfo {
            static final int COMMAND = 5;

            static MonitorInfo process(VirtualMachineImpl vm, ObjectReferenceImpl object) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.send();
                return ps;
            }

            static MonitorInfo waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new MonitorInfo(ps);
            }

            final com.myjdiproject.jdi.ThreadReference owner;
            final int entryCount;
            final List<com.myjdiproject.jdi.ThreadReference> waiters;

            private MonitorInfo(PacketStream ps) {
                owner = ps.readThreadReference();
                entryCount = ps.readInt();
                int waitersCount = ps.readInt();
                waiters = new ArrayList<>(waitersCount);
                for (int i = 0; i < waitersCount; i++) {
                    waiters.add(ps.readThreadReference());
                }
            }
        }

        static class InvokeMethod {
            static final int COMMAND = 6;

            static InvokeMethod process(VirtualMachineImpl vm, ObjectReferenceImpl object, ThreadReferenceImpl thread, ClassTypeImpl clazz, long methodID, List<Value> arguments, int options) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object, thread, clazz, methodID, arguments, options);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object, ThreadReferenceImpl thread, ClassTypeImpl clazz, long methodID, List<Value> arguments, int options) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.writeObjectRef(thread.ref());
                ps.writeClassRef(clazz.ref());
                ps.writeMethodRef(methodID);
                ps.writeInt(arguments.size());
                for (Value argument : arguments) {
                    ps.writeValue(argument);
                }
                ps.writeInt(options);
                ps.send();
                return ps;
            }

            static InvokeMethod waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new InvokeMethod(ps);
            }

            final ValueImpl returnValue;
            final ObjectReferenceImpl exception;

            private InvokeMethod(PacketStream ps) {
                returnValue = ps.readValue();
                exception = ps.readTaggedObjectReference();
            }
        }

        static class DisableCollection {
            static final int COMMAND = 7;

            static void process(VirtualMachineImpl vm, ObjectReferenceImpl object) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class EnableCollection {
            static final int COMMAND = 8;

            static void process(VirtualMachineImpl vm, ObjectReferenceImpl object) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class IsCollected {
            static final int COMMAND = 9;

            static IsCollected process(VirtualMachineImpl vm, ObjectReferenceImpl object) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.send();
                return ps;
            }

            static IsCollected waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new IsCollected(ps);
            }

            final boolean isCollected;

            private IsCollected(PacketStream ps) {
                isCollected = ps.readBoolean();
            }
        }

        static class ReferringObjects {
            static final int COMMAND = 10;

            static ReferringObjects process(VirtualMachineImpl vm, ObjectReferenceImpl object, int maxReferrers) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, object, maxReferrers);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ObjectReferenceImpl object, int maxReferrers) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(object.ref());
                ps.writeInt(maxReferrers);
                ps.send();
                return ps;
            }

            static ReferringObjects waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ReferringObjects(ps);
            }

            final List<com.myjdiproject.jdi.ObjectReference> referringObjects;

            private ReferringObjects(PacketStream ps) {
                int referringObjectsCount = ps.readInt();
                referringObjects = new ArrayList<>(referringObjectsCount);
                for (int i = 0; i < referringObjectsCount; i++) {
                    referringObjects.add(ps.readTaggedObjectReference());
                }
            }
        }
    }

    static class StringReference {
        static final int COMMAND_SET = 10;

        private StringReference() {}

        static class Value {
            static final int COMMAND = 1;

            static Value process(VirtualMachineImpl vm, StringReferenceImpl stringObject) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, stringObject);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, StringReferenceImpl stringObject) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(stringObject.ref());
                ps.send();
                return ps;
            }

            static Value waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Value(ps);
            }

            final String stringValue;

            private Value(PacketStream ps) {
                stringValue = ps.readString();
            }
        }
    }

    static class ThreadReference {
        static final int COMMAND_SET = 11;

        private ThreadReference() {}

        static class Name {
            static final int COMMAND = 1;

            static Name process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static Name waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Name(ps);
            }

            final String threadName;

            private Name(PacketStream ps) {
                threadName = ps.readString();
            }
        }

        static class Suspend {
            static final int COMMAND = 2;

            static void process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class Resume {
            static final int COMMAND = 3;

            static void process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class Status {
            static final int COMMAND = 4;

            static Status process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static Status waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Status(ps);
            }

            final int threadStatus;
            final int suspendStatus;

            private Status(PacketStream ps) {
                threadStatus = ps.readInt();
                suspendStatus = ps.readInt();
            }
        }

        static class ThreadGroup {
            static final int COMMAND = 5;

            static ThreadGroup process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                ps.waitForReply();
                return new ThreadGroup(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            final ThreadGroupReferenceImpl group;

            private ThreadGroup(PacketStream ps) {
                group = ps.readThreadGroupReference();
            }
        }

        static class Frames {
            static final int COMMAND = 6;

            static Frames process(VirtualMachineImpl vm, ThreadReferenceImpl thread, int startFrame, int length) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread, startFrame, length);
                ps.waitForReply();
                return new Frames(ps, vm, thread);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread, int startFrame, int length) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.writeInt(startFrame);
                ps.writeInt(length);
                ps.send();
                return ps;
            }

            final List<com.myjdiproject.jdi.StackFrame> frames;

            private Frames(PacketStream ps, VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                int framesCount = ps.readInt();
                frames = new ArrayList<>(framesCount);
                for (int i = 0; i < framesCount; i++) {
                    long frameID = ps.readFrameRef();
                    Location location = ps.readLocation();
                    frames.add(new StackFrameImpl(vm, thread, frameID, location));
                }
            }
        }

        static class FrameCount {
            static final int COMMAND = 7;

            static FrameCount process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static FrameCount waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new FrameCount(ps);
            }

            final int frameCount;

            private FrameCount(PacketStream ps) {
                frameCount = ps.readInt();
            }
        }

        static class OwnedMonitors {
            static final int COMMAND = 8;

            static OwnedMonitors process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static OwnedMonitors waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new OwnedMonitors(ps);
            }

            final List<com.myjdiproject.jdi.ObjectReference> owned;

            private OwnedMonitors(PacketStream ps) {
                int ownedCount = ps.readInt();
                owned = new ArrayList<>(ownedCount);
                for (int i = 0; i < ownedCount; i++) {
                    owned.add(ps.readTaggedObjectReference());
                }
            }
        }

        static class CurrentContendedMonitor {
            static final int COMMAND = 9;

            static CurrentContendedMonitor process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static CurrentContendedMonitor waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new CurrentContendedMonitor(ps);
            }

            final ObjectReferenceImpl monitor;

            private CurrentContendedMonitor(PacketStream ps) {
                monitor = ps.readTaggedObjectReference();
            }
        }

        static class Stop {
            static final int COMMAND = 10;

            static void process(VirtualMachineImpl vm, ThreadReferenceImpl thread, ObjectReferenceImpl throwable) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread, throwable);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread, ObjectReferenceImpl throwable) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.writeObjectRef(throwable.ref());
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class Interrupt {
            static final int COMMAND = 11;

            static void process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class SuspendCount {
            static final int COMMAND = 12;

            static SuspendCount process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static SuspendCount waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new SuspendCount(ps);
            }

            final int suspendCount;

            private SuspendCount(PacketStream ps) {
                suspendCount = ps.readInt();
            }
        }

        static class OwnedMonitorsStackDepthInfo {
            static final int COMMAND = 13;

            static OwnedMonitorsStackDepthInfo process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                ps.waitForReply();
                return new OwnedMonitorsStackDepthInfo(ps, vm, thread);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            final List<MonitorInfo> owned;

            private OwnedMonitorsStackDepthInfo(PacketStream ps, VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                int ownedCount = ps.readInt();
                owned = new ArrayList<>(ownedCount);
                for (int i = 0; i < ownedCount; i++) {
                    ObjectReferenceImpl monitor = ps.readTaggedObjectReference();
                    int stackDepth = ps.readInt();
                    owned.add(new MonitorInfoImpl(vm, monitor, thread, stackDepth));
                }
            }
        }

        static class ForceEarlyReturn {
            static final int COMMAND = 14;

            static void process(VirtualMachineImpl vm, ThreadReferenceImpl thread, ValueImpl value) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread, value);
                ps.waitForReply();
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread, ValueImpl value) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.writeValue(value);
                ps.send();
                return ps;
            }
        }

        static class IsVirtual {
            static final int COMMAND = 15;

            static IsVirtual process(VirtualMachineImpl vm, ThreadReferenceImpl thread) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.send();
                return ps;
            }

            static IsVirtual waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new IsVirtual(ps);
            }

            final boolean isVirtual;

            private IsVirtual(PacketStream ps) {
                isVirtual = ps.readBoolean();
            }
        }
    }

    static class ThreadGroupReference {
        static final int COMMAND_SET = 12;

        private ThreadGroupReference() {}

        static class Name {
            static final int COMMAND = 1;

            static Name process(VirtualMachineImpl vm, ThreadGroupReferenceImpl group) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, group);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadGroupReferenceImpl group) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(group.ref());
                ps.send();
                return ps;
            }

            static Name waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Name(ps);
            }

            final String groupName;

            private Name(PacketStream ps) {
                groupName = ps.readString();
            }
        }

        static class Parent {
            static final int COMMAND = 2;

            static Parent process(VirtualMachineImpl vm, ThreadGroupReferenceImpl group) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, group);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadGroupReferenceImpl group) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(group.ref());
                ps.send();
                return ps;
            }

            static Parent waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Parent(ps);
            }

            final ThreadGroupReferenceImpl parentGroup;

            private Parent(PacketStream ps) {
                parentGroup = ps.readThreadGroupReference();
            }
        }

        static class Children {
            static final int COMMAND = 3;

            static Children process(VirtualMachineImpl vm, ThreadGroupReferenceImpl group) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, group);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadGroupReferenceImpl group) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(group.ref());
                ps.send();
                return ps;
            }

            static Children waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Children(ps);
            }

            final List<com.myjdiproject.jdi.ThreadReference> childThreads;
            final List<com.myjdiproject.jdi.ThreadGroupReference> childGroups;

            private Children(PacketStream ps) {
                int childThreadsCount = ps.readInt();
                childThreads = new ArrayList<>(childThreadsCount);
                for (int i = 0; i < childThreadsCount; i++) {
                    childThreads.add(ps.readThreadReference());
                }
                int childGroupsCount = ps.readInt();
                childGroups = new ArrayList<>(childGroupsCount);
                for (int i = 0; i < childGroupsCount; i++) {
                    childGroups.add(ps.readThreadGroupReference());
                }
            }
        }
    }

    static class ArrayReference {
        static final int COMMAND_SET = 13;
        private ArrayReference() {}

        static class Length {
            static final int COMMAND = 1;

            static Length process(VirtualMachineImpl vm, ArrayReferenceImpl arrayObject) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, arrayObject);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ArrayReferenceImpl arrayObject) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(arrayObject.ref());
                ps.send();
                return ps;
            }

            static Length waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Length(ps);
            }

            final int arrayLength;

            private Length(PacketStream ps) {
                arrayLength = ps.readInt();
            }
        }

        static class GetValues {
            static final int COMMAND = 2;

            static GetValues process(VirtualMachineImpl vm, ArrayReferenceImpl arrayObject, int firstIndex, int length) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, arrayObject, firstIndex, length);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ArrayReferenceImpl arrayObject, int firstIndex, int length) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(arrayObject.ref());
                ps.writeInt(firstIndex);
                ps.writeInt(length);
                ps.send();
                return ps;
            }

            static GetValues waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new GetValues(ps);
            }

            final List<?> values;

            private GetValues(PacketStream ps) {
                values = ps.readArrayRegion();
            }
        }

        static class SetValues {
            static final int COMMAND = 3;

            static void process(VirtualMachineImpl vm, ArrayReferenceImpl arrayObject, int firstIndex, List<Value> values) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, arrayObject, firstIndex, values);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ArrayReferenceImpl arrayObject, int firstIndex, List<Value> values) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(arrayObject.ref());
                ps.writeInt(firstIndex);
                ps.writeArrayRegion(values);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }
    }

    static class ClassLoaderReference {
        static final int COMMAND_SET = 14;

        private ClassLoaderReference() {}

        static class VisibleClasses {
            static final int COMMAND = 1;

            static VisibleClasses process(VirtualMachineImpl vm, ClassLoaderReferenceImpl classLoaderObject) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, classLoaderObject);
                ps.waitForReply();
                return new VisibleClasses(ps, vm);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ClassLoaderReferenceImpl classLoaderObject) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(classLoaderObject.ref());
                ps.send();
                return ps;
            }

            final List<com.myjdiproject.jdi.ReferenceType> classes;

            private VisibleClasses(PacketStream ps, VirtualMachineImpl vm) {
                int classesCount = ps.readInt();
                classes = new ArrayList<>(classesCount);
                for (int i = 0; i < classesCount; i++) {
                    byte refTypeTag = ps.readByte();
                    long typeID = ps.readClassRef();
                    classes.add(vm.referenceType(typeID, refTypeTag));
                }
            }
        }
    }

    static class EventRequest {
        static final int COMMAND_SET = 15;

        private EventRequest() {}

        static class Set {
            static final int COMMAND = 1;

            record Modifier(byte modKind, Set.Modifier.ModifierCommon aModifierCommon) {
                abstract static class ModifierCommon {
                    abstract void write(PacketStream ps);
                }

                private void write(PacketStream ps) {
                    ps.writeByte(modKind);
                    aModifierCommon.write(ps);
                }

                static class Count extends ModifierCommon {
                    static final byte ALT_ID = 1;

                    static Modifier create(int count) {
                        return new Modifier(ALT_ID, new Count(count));
                    }

                    final int count;

                    Count(int count) {
                        this.count = count;
                    }

                    void write(PacketStream ps) {
                        ps.writeInt(count);
                    }
                }

                static class ThreadOnly extends ModifierCommon {
                    static final byte ALT_ID = 3;

                    static Modifier create(ThreadReferenceImpl thread) {
                        return new Modifier(ALT_ID, new ThreadOnly(thread));
                    }

                    final ThreadReferenceImpl thread;

                    ThreadOnly(ThreadReferenceImpl thread) {
                        this.thread = thread;
                    }

                    void write(PacketStream ps) {
                        ps.writeObjectRef(thread.ref());
                    }
                }

                static class ClassOnly extends ModifierCommon {
                    static final byte ALT_ID = 4;

                    static Modifier create(ReferenceTypeImpl clazz) {
                        return new Modifier(ALT_ID, new ClassOnly(clazz));
                    }

                    final ReferenceTypeImpl clazz;

                    ClassOnly(ReferenceTypeImpl clazz) {
                        this.clazz = clazz;
                    }

                    void write(PacketStream ps) {
                        ps.writeClassRef(clazz.ref());
                    }
                }

                static class ClassMatch extends ModifierCommon {
                    static final byte ALT_ID = 5;

                    static Modifier create(String classPattern) {
                        return new Modifier(ALT_ID, new ClassMatch(classPattern));
                    }

                    final String classPattern;

                    ClassMatch(String classPattern) {
                        this.classPattern = classPattern;
                    }

                    void write(PacketStream ps) {
                        ps.writeString(classPattern);
                    }
                }

                static class ClassExclude extends ModifierCommon {
                    static final byte ALT_ID = 6;

                    static Modifier create(String classPattern) {
                        return new Modifier(ALT_ID, new ClassExclude(classPattern));
                    }

                    final String classPattern;

                    ClassExclude(String classPattern) {
                        this.classPattern = classPattern;
                    }

                    void write(PacketStream ps) {
                        ps.writeString(classPattern);
                    }
                }

                static class ClassSetExclude extends ModifierCommon {
                    static final byte ALT_ID = 15;

                    static Modifier create(java.util.Set<String> classNames) {
                        return new Modifier(ALT_ID, new ClassSetExclude(classNames));
                    }

                    final java.util.Set<String> classNames;

                    ClassSetExclude(java.util.Set<String> classNames) {
                        this.classNames = new HashSet<>(classNames);
                    }

                    void write(PacketStream ps) {
                        ps.writeInt(classNames.size());
                        for (String className : classNames) {
                            ps.writeString(className);
                        }
                    }
                }

                static class LocationOnly extends ModifierCommon {
                    static final byte ALT_ID = 7;

                    static Modifier create(Location loc) {
                        return new Modifier(ALT_ID, new LocationOnly(loc));
                    }

                    final Location loc;

                    LocationOnly(Location loc) {
                        this.loc = loc;
                    }

                    void write(PacketStream ps) {
                        ps.writeLocation(loc);
                    }
                }

                static class ExceptionOnly extends ModifierCommon {
                    static final byte ALT_ID = 8;

                    static Modifier create(ReferenceTypeImpl exceptionOrNull, boolean caught, boolean uncaught) {
                        return new Modifier(ALT_ID, new ExceptionOnly(exceptionOrNull, caught, uncaught));
                    }

                    final ReferenceTypeImpl exceptionOrNull;
                    final boolean caught;
                    final boolean uncaught;

                    ExceptionOnly(ReferenceTypeImpl exceptionOrNull, boolean caught, boolean uncaught) {
                        this.exceptionOrNull = exceptionOrNull;
                        this.caught = caught;
                        this.uncaught = uncaught;
                    }

                    void write(PacketStream ps) {
                        ps.writeClassRef(exceptionOrNull.ref());
                        ps.writeBoolean(caught);
                        ps.writeBoolean(uncaught);
                    }
                }

                static class FieldOnly extends ModifierCommon {
                    static final byte ALT_ID = 9;

                    static Modifier create(ReferenceTypeImpl declaring, long fieldID) {
                        return new Modifier(ALT_ID, new FieldOnly(declaring, fieldID));
                    }

                    final ReferenceTypeImpl declaring;
                    final long fieldID;

                    FieldOnly(ReferenceTypeImpl declaring, long fieldID) {
                        this.declaring = declaring;
                        this.fieldID = fieldID;
                    }

                    void write(PacketStream ps) {
                        ps.writeClassRef(declaring.ref());
                        ps.writeFieldRef(fieldID);
                    }
                }

                static class Step extends ModifierCommon {
                    static final byte ALT_ID = 10;

                    static Modifier create(ThreadReferenceImpl thread, int size, int depth) {
                        return new Modifier(ALT_ID, new Step(thread, size, depth));
                    }

                    final ThreadReferenceImpl thread;
                    final int size;
                    final int depth;

                    Step(ThreadReferenceImpl thread, int size, int depth) {
                        this.thread = thread;
                        this.size = size;
                        this.depth = depth;
                    }

                    void write(PacketStream ps) {
                        ps.writeObjectRef(thread.ref());
                        ps.writeInt(size);
                        ps.writeInt(depth);
                    }
                }

                static class InstanceOnly extends ModifierCommon {
                    static final byte ALT_ID = 11;

                    static Modifier create(ObjectReferenceImpl instance) {
                        return new Modifier(ALT_ID, new InstanceOnly(instance));
                    }

                    final ObjectReferenceImpl instance;

                    InstanceOnly(ObjectReferenceImpl instance) {
                        this.instance = instance;
                    }

                    void write(PacketStream ps) {
                        ps.writeObjectRef(instance.ref());
                    }
                }

                static class SourceNameMatch extends ModifierCommon {
                    static final byte ALT_ID = 12;

                    static Modifier create(String sourceNamePattern) {
                        return new Modifier(ALT_ID, new SourceNameMatch(sourceNamePattern));
                    }

                    final String sourceNamePattern;

                    SourceNameMatch(String sourceNamePattern) {
                        this.sourceNamePattern = sourceNamePattern;
                    }

                    void write(PacketStream ps) {
                        ps.writeString(sourceNamePattern);
                    }
                }

                static class PlatformThreadsOnly extends ModifierCommon {
                    static final byte ALT_ID = 13;

                    static Modifier create() {
                        return new Modifier(ALT_ID, new PlatformThreadsOnly());
                    }

                    PlatformThreadsOnly() {
                    }

                    void write(PacketStream ps) {
                    }
                }

                static class MethodOnly extends ModifierCommon {
                    static final byte ALT_ID = 14;

                    static Modifier create(com.myjdiproject.jdi.Method method) {
                        return new Modifier(ALT_ID, new MethodOnly(method));
                    }

                    final com.myjdiproject.jdi.Method method;

                    MethodOnly(com.myjdiproject.jdi.Method method) {
                        this.method = method;
                    }

                    void write(PacketStream ps) {
                        ps.writeClassRef(method.declaringType().ref());
                        ps.writeMethodRef(method.ref());
                    }
                }
            }

            static Set process(VirtualMachineImpl vm, byte eventKind, byte suspendPolicy, List<JDWP.EventRequest.Set.Modifier> modifiers) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, eventKind, suspendPolicy, modifiers);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, byte eventKind, byte suspendPolicy, List<JDWP.EventRequest.Set.Modifier> modifiers) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeByte(eventKind);
                ps.writeByte(suspendPolicy);
                ps.writeInt(modifiers.size());
                for (JDWP.EventRequest.Set.Modifier modifier : modifiers) {
                    modifier.write(ps);
                }
                ps.send();
                return ps;
            }

            static Set waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Set(ps);
            }

            final int requestID;

            private Set(PacketStream ps) {
                requestID = ps.readInt();
            }
        }

        static class Clear {
            static final int COMMAND = 2;

            static void process(VirtualMachineImpl vm, byte eventKind, int requestID) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, eventKind, requestID);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, byte eventKind, int requestID) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeByte(eventKind);
                ps.writeInt(requestID);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class ClearAllBreakpoints {
            static final int COMMAND = 3;

            static void process(VirtualMachineImpl vm) throws JDWPException {
                PacketStream ps = enqueueCommand(vm);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }
    }

    static class StackFrame {
        static final int COMMAND_SET = 16;
        private StackFrame() {}

        static class GetValues {
            static final int COMMAND = 1;

            record SlotInfo(int slot, byte sigbyte) {
                private void write(PacketStream ps) {
                    ps.writeInt(slot);
                    ps.writeByte(sigbyte);
                }
            }

            static GetValues process(VirtualMachineImpl vm, ThreadReferenceImpl thread, long frame, List<SlotInfo> slots) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread, frame, slots);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread, long frame, List<SlotInfo> slots) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.writeFrameRef(frame);
                ps.writeInt(slots.size());
                for (SlotInfo slot : slots) {
                    slot.write(ps);
                }
                ps.send();
                return ps;
            }

            static GetValues waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new GetValues(ps);
            }

            final List<Value> values;

            private GetValues(PacketStream ps) {
                int valuesCount = ps.readInt();
                values = new ArrayList<>(valuesCount);
                for (int i = 0; i < valuesCount; i++) {
                    values.add(ps.readValue());
                }
            }
        }

        static class SetValues {
            static final int COMMAND = 2;

            record SlotInfo(int slot, Value slotValue) {
                private void write(PacketStream ps) {
                    ps.writeInt(slot);
                    ps.writeValue(slotValue);
                }
            }

            static void process(VirtualMachineImpl vm, ThreadReferenceImpl thread, long frame, List<SlotInfo> slotValues) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread, frame, slotValues);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread, long frame, List<SlotInfo> slotValues) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.writeFrameRef(frame);
                ps.writeInt(slotValues.size());
                for (SlotInfo slotValue : slotValues) {
                    slotValue.write(ps);
                }
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }

        static class ThisObject {
            static final int COMMAND = 3;

            static ThisObject process(VirtualMachineImpl vm, ThreadReferenceImpl thread, long frame) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread, frame);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread, long frame) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.writeFrameRef(frame);
                ps.send();
                return ps;
            }

            static ThisObject waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ThisObject(ps);
            }

            final ObjectReferenceImpl objectThis;

            private ThisObject(PacketStream ps) {
                objectThis = ps.readTaggedObjectReference();
            }
        }

        static class PopFrames {
            static final int COMMAND = 4;

            static void process(VirtualMachineImpl vm, ThreadReferenceImpl thread, long frame) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, thread, frame);
                waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ThreadReferenceImpl thread, long frame) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(thread.ref());
                ps.writeFrameRef(frame);
                ps.send();
                return ps;
            }

            static void waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
            }
        }
    }

    static class ClassObjectReference {
        static final int COMMAND_SET = 17;
        private ClassObjectReference() {}

        static class ReflectedType {
            static final int COMMAND = 1;

            static ReflectedType process(VirtualMachineImpl vm, ClassObjectReferenceImpl classObject) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, classObject);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ClassObjectReferenceImpl classObject) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeObjectRef(classObject.ref());
                ps.send();
                return ps;
            }

            static ReflectedType waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ReflectedType(ps);
            }

            final byte refTypeTag;
            final long typeID;

            private ReflectedType(PacketStream ps) {
                refTypeTag = ps.readByte();
                typeID = ps.readClassRef();
            }
        }
    }

    static class ModuleReference {
        static final int COMMAND_SET = 18;
        private ModuleReference() {}

        static class Name {
            static final int COMMAND = 1;

            static Name process(VirtualMachineImpl vm, ModuleReferenceImpl module) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, module);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ModuleReferenceImpl module) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeModuleRef(module.ref());
                ps.send();
                return ps;
            }

            static Name waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new Name(ps);
            }

            final String name;

            private Name(PacketStream ps) {
                name = ps.readString();
            }
        }

        static class ClassLoader {
            static final int COMMAND = 2;

            static ClassLoader process(VirtualMachineImpl vm, ModuleReferenceImpl module) throws JDWPException {
                PacketStream ps = enqueueCommand(vm, module);
                return waitForReply(ps);
            }

            static PacketStream enqueueCommand(VirtualMachineImpl vm, ModuleReferenceImpl module) {
                PacketStream ps = new PacketStream(vm, COMMAND_SET, COMMAND);
                ps.writeModuleRef(module.ref());
                ps.send();
                return ps;
            }

            static ClassLoader waitForReply(PacketStream ps) throws JDWPException {
                ps.waitForReply();
                return new ClassLoader(ps);
            }

            final ClassLoaderReferenceImpl classLoader;

            private ClassLoader(PacketStream ps) {
                classLoader = ps.readClassLoaderReference();
            }
        }
    }

    static class Event {
        static final int COMMAND_SET = 64;
        private Event() {}

        static class Composite {
            static final int COMMAND = 100;

            static class Events {
                abstract static class EventsCommon {
                    abstract byte eventKind();
                }
            }

            final byte suspendPolicy;
            final List<EventSetImpl.EventImpl> events;

            Composite(PacketStream ps, VirtualMachineImpl vm) {
                suspendPolicy = ps.readByte();
                int eventsCount = ps.readInt();
                events = new ArrayList<>(eventsCount);
                for (int i = 0; i < eventsCount; i++) {
                    events.add(parseEvent(ps, vm));
                }
            }

            private static EventSetImpl.EventImpl parseEvent(PacketStream ps, VirtualMachineImpl vm) {
                byte eventKind = ps.readByte();
                return switch (eventKind) {
                    case JDWP.EventKind.BREAKPOINT -> new EventSetImpl.BreakpointEventImpl(ps, vm);
                    case JDWP.EventKind.CLASS_PREPARE -> new EventSetImpl.ClassPrepareEventImpl(ps, vm);
                    case JDWP.EventKind.CLASS_UNLOAD -> new EventSetImpl.ClassUnloadEventImpl(ps, vm);
                    case JDWP.EventKind.EXCEPTION -> new EventSetImpl.ExceptionEventImpl(ps, vm);
                    case JDWP.EventKind.FIELD_ACCESS -> new EventSetImpl.AccessWatchpointEventImpl(ps, vm);
                    case JDWP.EventKind.FIELD_MODIFICATION -> new EventSetImpl.ModificationWatchpointEventImpl(ps, vm);
                    case JDWP.EventKind.METHOD_ENTRY -> new EventSetImpl.MethodEntryEventImpl(ps, vm);
                    case JDWP.EventKind.METHOD_EXIT -> new EventSetImpl.MethodExitEventImpl(ps, vm, false);
                    case JDWP.EventKind.METHOD_EXIT_WITH_RETURN_VALUE -> new EventSetImpl.MethodExitEventImpl(ps, vm, true);
                    case JDWP.EventKind.MONITOR_CONTENDED_ENTER -> new EventSetImpl.MonitorContendedEnterEventImpl(ps, vm);
                    case JDWP.EventKind.MONITOR_CONTENDED_ENTERED -> new EventSetImpl.MonitorContendedEnteredEventImpl(ps, vm);
                    case JDWP.EventKind.MONITOR_WAIT -> new EventSetImpl.MonitorWaitEventImpl(ps, vm);
                    case JDWP.EventKind.MONITOR_WAITED -> new EventSetImpl.MonitorWaitedEventImpl(ps, vm);
                    case JDWP.EventKind.SINGLE_STEP -> new EventSetImpl.StepEventImpl(ps, vm);
                    case JDWP.EventKind.THREAD_DEATH -> new EventSetImpl.ThreadDeathEventImpl(ps, vm);
                    case JDWP.EventKind.THREAD_START -> new EventSetImpl.ThreadStartEventImpl(ps, vm);
                    case JDWP.EventKind.VM_DEATH -> new EventSetImpl.VMDeathEventImpl(ps, vm);
                    case JDWP.EventKind.VM_START -> new EventSetImpl.VMStartEventImpl(ps, vm);
                    default -> {
                        System.err.println("Ignoring event cmd " + eventKind + " from the VM");
                        yield null;
                    }
                };
            }
        }
    }

    static class Error {
        static final int NONE = 0;
        static final int INVALID_THREAD = 10;
        static final int INVALID_THREAD_GROUP = 11;
        static final int INVALID_PRIORITY = 12;
        static final int THREAD_NOT_SUSPENDED = 13;
        static final int THREAD_SUSPENDED = 14;
        static final int THREAD_NOT_ALIVE = 15;
        static final int INVALID_OBJECT = 20;
        static final int INVALID_CLASS = 21;
        static final int CLASS_NOT_PREPARED = 22;
        static final int INVALID_METHODID = 23;
        static final int INVALID_LOCATION = 24;
        static final int INVALID_FIELDID = 25;
        static final int INVALID_FRAMEID = 30;
        static final int NO_MORE_FRAMES = 31;
        static final int OPAQUE_FRAME = 32;
        static final int NOT_CURRENT_FRAME = 33;
        static final int TYPE_MISMATCH = 34;
        static final int INVALID_SLOT = 35;
        static final int DUPLICATE = 40;
        static final int NOT_FOUND = 41;
        static final int INVALID_MODULE = 42;
        static final int INVALID_MONITOR = 50;
        static final int NOT_MONITOR_OWNER = 51;
        static final int INTERRUPT = 52;
        static final int INVALID_CLASS_FORMAT = 60;
        static final int CIRCULAR_CLASS_DEFINITION = 61;
        static final int FAILS_VERIFICATION = 62;
        static final int ADD_METHOD_NOT_IMPLEMENTED = 63;
        static final int SCHEMA_CHANGE_NOT_IMPLEMENTED = 64;
        static final int INVALID_TYPESTATE = 65;
        static final int HIERARCHY_CHANGE_NOT_IMPLEMENTED = 66;
        static final int DELETE_METHOD_NOT_IMPLEMENTED = 67;
        static final int UNSUPPORTED_VERSION = 68;
        static final int NAMES_DONT_MATCH = 69;
        static final int CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED = 70;
        static final int METHOD_MODIFIERS_CHANGE_NOT_IMPLEMENTED = 71;
        static final int CLASS_ATTRIBUTE_CHANGE_NOT_IMPLEMENTED = 72;
        static final int NOT_IMPLEMENTED = 99;
        static final int NULL_POINTER = 100;
        static final int ABSENT_INFORMATION = 101;
        static final int INVALID_EVENT_TYPE = 102;
        static final int ILLEGAL_ARGUMENT = 103;
        static final int OUT_OF_MEMORY = 110;
        static final int ACCESS_DENIED = 111;
        static final int VM_DEAD = 112;
        static final int INTERNAL = 113;
        static final int UNATTACHED_THREAD = 115;
        static final int INVALID_TAG = 500;
        static final int ALREADY_INVOKING = 502;
        static final int INVALID_INDEX = 503;
        static final int INVALID_LENGTH = 504;
        static final int INVALID_STRING = 506;
        static final int INVALID_CLASS_LOADER = 507;
        static final int INVALID_ARRAY = 508;
        static final int TRANSPORT_LOAD = 509;
        static final int TRANSPORT_INIT = 510;
        static final int NATIVE_METHOD = 511;
        static final int INVALID_COUNT = 512;
    }

    static class EventKind {
        static final int SINGLE_STEP = 1;
        static final int BREAKPOINT = 2;
        static final int FRAME_POP = 3;
        static final int EXCEPTION = 4;
        static final int USER_DEFINED = 5;
        static final int THREAD_START = 6;
        static final int THREAD_DEATH = 7;
        static final int THREAD_END = 7;
        static final int CLASS_PREPARE = 8;
        static final int CLASS_UNLOAD = 9;
        static final int CLASS_LOAD = 10;
        static final int FIELD_ACCESS = 20;
        static final int FIELD_MODIFICATION = 21;
        static final int EXCEPTION_CATCH = 30;
        static final int METHOD_ENTRY = 40;
        static final int METHOD_EXIT = 41;
        static final int METHOD_EXIT_WITH_RETURN_VALUE = 42;
        static final int MONITOR_CONTENDED_ENTER = 43;
        static final int MONITOR_CONTENDED_ENTERED = 44;
        static final int MONITOR_WAIT = 45;
        static final int MONITOR_WAITED = 46;
        static final int VM_START = 90;
        static final int VM_INIT = 90;
        static final int VM_DEATH = 99;
        static final int VM_DISCONNECTED = 100;
    }

    static class ThreadStatus {
        static final int ZOMBIE = 0;
        static final int RUNNING = 1;
        static final int SLEEPING = 2;
        static final int MONITOR = 3;
        static final int WAIT = 4;
    }

    static class SuspendStatus {
        static final int SUSPEND_STATUS_SUSPENDED = 0x1;
    }

    static class ClassStatus {
        static final int VERIFIED = 1;
        static final int PREPARED = 2;
        static final int INITIALIZED = 4;
        static final int ERROR = 8;
    }

    static class TypeTag {
        static final int CLASS = 1;
        static final int INTERFACE = 2;
        static final int ARRAY = 3;
    }

    static class Tag {
        static final int ARRAY = 91;
        static final int BYTE = 66;
        static final int CHAR = 67;
        static final int OBJECT = 76;
        static final int FLOAT = 70;
        static final int DOUBLE = 68;
        static final int INT = 73;
        static final int LONG = 74;
        static final int SHORT = 83;
        static final int VOID = 86;
        static final int BOOLEAN = 90;
        static final int STRING = 115;
        static final int THREAD = 116;
        static final int THREAD_GROUP = 103;
        static final int CLASS_LOADER = 108;
        static final int CLASS_OBJECT = 99;
    }

    static class StepDepth {
        static final int INTO = 0;
        static final int OVER = 1;
        static final int OUT = 2;
    }

    static class StepSize {
        static final int MIN = 0;
        static final int LINE = 1;
    }

    static class SuspendPolicy {
        static final int NONE = 0;
        static final int EVENT_THREAD = 1;
        static final int ALL = 2;
    }

    static class InvokeOptions {
        static final int INVOKE_SINGLE_THREADED = 0x01;
        static final int INVOKE_NONVIRTUAL = 0x02;
    }
}
