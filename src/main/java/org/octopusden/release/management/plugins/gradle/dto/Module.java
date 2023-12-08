package org.octopusden.release.management.plugins.gradle.dto;

public class Module {
    private String group;
    private String module;

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    @Override
    public String toString() {
        return group + ":" + module;
    }
}
