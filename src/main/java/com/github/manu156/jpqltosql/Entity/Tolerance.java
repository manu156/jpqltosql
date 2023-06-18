package com.github.manu156.jpqltosql.Entity;

import com.github.manu156.jpqltosql.Execption.FailedTranslation;

public class Tolerance {
    public boolean tolerance;
    public long violations;

    public Tolerance(boolean tolerance) {
        this.tolerance = tolerance;
        violations = 0L;
    }

    public String getValue(String value) {
        if (!tolerance)
            throw new FailedTranslation();

        violations += 1;
        return value;
    }
}
