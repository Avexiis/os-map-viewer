package com.xeon.view;

enum AtlasLayerType
{
    BASE("base"),
    ICONS("icons"),
    LABELS("labels");

    final String metadataName;

    AtlasLayerType(String metadataName) {
        this.metadataName = metadataName;
    }

    static AtlasLayerType fromMetadataName(String value) {
        for (AtlasLayerType kind : values()) {
            if (kind.metadataName.equals(value)) {
                return kind;
            }
        }
        return null;
    }
}
