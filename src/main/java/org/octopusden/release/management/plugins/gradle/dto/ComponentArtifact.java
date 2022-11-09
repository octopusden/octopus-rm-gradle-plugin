package org.octopusden.release.management.plugins.gradle.dto;

import java.util.Objects;

public class ComponentArtifact {
    private String group;
    private String name;
    private String version;

    public ComponentArtifact(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "ComponentArtifact{" +
                "group='" + group + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentArtifact)) return false;
        ComponentArtifact that = (ComponentArtifact) o;
        return Objects.equals(group, that.group) && Objects.equals(name, that.name) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name, version);
    }
}
