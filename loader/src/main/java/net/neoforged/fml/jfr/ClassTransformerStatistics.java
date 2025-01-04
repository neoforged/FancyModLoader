/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;
import jdk.jfr.Unsigned;

@Name(ClassTransformerStatistics.NAME)
@Label("Class Transformer Statistics")
@Category({ "NeoForge", "Loader" })
@Description("Statistics of the FML class transformer")
@StackTrace(false)
@Period("5 s")
public class ClassTransformerStatistics extends Event {
    static final String NAME = "fml.ClassTransformerStatistics";
    public static final EventType TYPE = EventType.getEventType(ClassTransformerStatistics.class);

    @Name("classesTransformed")
    @Unsigned
    public final long classesTransformed;

    @Name("classParsingTime")
    @Timespan
    public final long classParsingTime;

    @Name("classTransformingTime")
    @Timespan
    public final long classTransformingTime;

    public ClassTransformerStatistics(long classesTransformed, long classParsingTime, long classTransformingTime) {
        this.classesTransformed = classesTransformed;
        this.classParsingTime = classParsingTime;
        this.classTransformingTime = classTransformingTime;
    }
}
