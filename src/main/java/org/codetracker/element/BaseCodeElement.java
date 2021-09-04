package org.codetracker.element;

import org.codetracker.api.CodeElement;
import org.codetracker.api.Version;

import java.util.Objects;

public abstract class BaseCodeElement implements CodeElement {
    protected final String identifier;
    protected final String identifierIgnoringVersion;
    protected final String name;
    protected final String filePath;
    protected final Version version;
    protected boolean isRemoved;
    protected boolean isAdded;

    public BaseCodeElement(String identifierIgnoringVersion, String name, String filePath, Version version) {
        this.identifier = version != null ? identifierIgnoringVersion + version : identifierIgnoringVersion;
        this.identifierIgnoringVersion = identifierIgnoringVersion;
        this.name = name;
        this.filePath = filePath;
        this.version = version;
    }


    @Override
    public final int compareTo(CodeElement o) {
        return this.name.compareTo(o.getName());
    }

    @Override
    public final Version getVersion() {
        return version;
    }

    @Override
    public String getFilePath() {
        return filePath;
    }

    public final boolean isRemoved() {
        return isRemoved;
    }

    public final void setRemoved(boolean removed) {
        isRemoved = removed;
    }

    public final boolean isAdded() {
        return isAdded;
    }

    public final void setAdded(boolean added) {
        isAdded = added;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean equalIdentifierIgnoringVersion(BaseCodeElement codeElement) {
        return this.identifierIgnoringVersion.equals(codeElement.identifierIgnoringVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseCodeElement that = (BaseCodeElement) o;
        return Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }

    @Override
    public String toString() {
        if (version != null)
            return String.format("%s%s", name, version);
        return name;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getIdentifierIgnoringVersion() {
        return identifierIgnoringVersion;
    }

}
