package com.example.samajconnectbackend.entity;

public enum RelationshipType {
    // Direct Family
    FATHER("Father", "DIRECT", -1),
    MOTHER("Mother", "DIRECT", -1),
    SON("Son", "DIRECT", 1),
    DAUGHTER("Daughter", "DIRECT", 1),
    HUSBAND("Husband", "DIRECT", 0),
    WIFE("Wife", "DIRECT", 0),
    BROTHER("Brother", "DIRECT", 0),
    SISTER("Sister", "DIRECT", 0),

    // Paternal Side
    PATERNAL_GRANDFATHER("Paternal Grandfather", "PATERNAL", -2),
    PATERNAL_GRANDMOTHER("Paternal Grandmother", "PATERNAL", -2),
    PATERNAL_UNCLE("Paternal Uncle", "PATERNAL", -1),
    PATERNAL_AUNT("Paternal Aunt", "PATERNAL", -1),
    PATERNAL_COUSIN_BROTHER("Paternal Cousin Brother", "PATERNAL", 0),
    PATERNAL_COUSIN_SISTER("Paternal Cousin Sister", "PATERNAL", 0),

    // Maternal Side
    MATERNAL_GRANDFATHER("Maternal Grandfather", "MATERNAL", -2),
    MATERNAL_GRANDMOTHER("Maternal Grandmother", "MATERNAL", -2),
    MATERNAL_UNCLE("Maternal Uncle", "MATERNAL", -1),
    MATERNAL_AUNT("Maternal Aunt", "MATERNAL", -1),
    MATERNAL_COUSIN_BROTHER("Maternal Cousin Brother", "MATERNAL", 0),
    MATERNAL_COUSIN_SISTER("Maternal Cousin Sister", "MATERNAL", 0),

    // In-Laws (Spouse Family)
    FATHER_IN_LAW("Father-in-Law", "SPOUSE_FAMILY", -1),
    MOTHER_IN_LAW("Mother-in-Law", "SPOUSE_FAMILY", -1),
    BROTHER_IN_LAW("Brother-in-Law", "SPOUSE_FAMILY", 0),
    SISTER_IN_LAW("Sister-in-Law", "SPOUSE_FAMILY", 0),
    SON_IN_LAW("Son-in-Law", "SPOUSE_FAMILY", 1),
    DAUGHTER_IN_LAW("Daughter-in-Law", "SPOUSE_FAMILY", 1),

    // Extended Family
    NEPHEW("Nephew", "DIRECT", 1),
    NIECE("Niece", "DIRECT", 1),
    GRANDSON("Grandson", "DIRECT", 2),
    GRANDDAUGHTER("Granddaughter", "DIRECT", 2),
    GREAT_GRANDFATHER("Great Grandfather", "DIRECT", -3),
    GREAT_GRANDMOTHER("Great Grandmother", "DIRECT", -3),
    GREAT_GRANDSON("Great Grandson", "DIRECT", 3),
    GREAT_GRANDDAUGHTER("Great Granddaughter", "DIRECT", 3),

    // Step Family
    STEP_FATHER("Step Father", "STEP_FAMILY", -1),
    STEP_MOTHER("Step Mother", "STEP_FAMILY", -1),
    STEP_BROTHER("Step Brother", "STEP_FAMILY", 0),
    STEP_SISTER("Step Sister", "STEP_FAMILY", 0),
    STEP_SON("Step Son", "STEP_FAMILY", 1),
    STEP_DAUGHTER("Step Daughter", "STEP_FAMILY", 1);

    private final String displayName;
    private final String defaultSide;
    private final int defaultGenerationLevel;

    RelationshipType(String displayName, String defaultSide, int defaultGenerationLevel) {
        this.displayName = displayName;
        this.defaultSide = defaultSide;
        this.defaultGenerationLevel = defaultGenerationLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultSide() {
        return defaultSide;
    }

    public int getDefaultGenerationLevel() {
        return defaultGenerationLevel;
    }

    public RelationshipSide getDefaultRelationshipSide() {
        return RelationshipSide.valueOf(defaultSide);
    }
}