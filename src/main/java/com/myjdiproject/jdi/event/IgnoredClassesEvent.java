package com.myjdiproject.jdi.event;

import java.util.List;

// SCANNER ADDED
// Non-suspending event carrying a batch of class names that the target VM's RuleIndex
// class filter dropped (together with their supertype hierarchy). The host caches these
// names so future sessions skip them before the RuleIndex check.
public interface IgnoredClassesEvent extends Event {
    List<String> classNames();
}
