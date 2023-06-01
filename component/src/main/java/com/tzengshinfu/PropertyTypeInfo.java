package com.tzengshinfu;

public class PropertyTypeInfo {
    private final String originalTypeName;
    private final String simpleTypeName;
    private final String propertyName;

    public PropertyTypeInfo(String originalTypeName, String simpleTypeName, String propertyName) {
        this.originalTypeName = originalTypeName;
        this.simpleTypeName = simpleTypeName;
        this.propertyName = propertyName;
    }

    public String getOriginalTypeName() {
        return this.originalTypeName;
    }

    public String getSimpleTypeName() {
        return this.simpleTypeName;
    }

    public String getPropertyName() {
        return this.propertyName;
    }
}
