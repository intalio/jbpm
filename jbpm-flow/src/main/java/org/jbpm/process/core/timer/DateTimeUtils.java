package org.jbpm.process.core.timer;

import org.drools.core.time.TimeUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.ISOPeriodFormat;

public class DateTimeUtils extends TimeUtils {
    
    public static boolean isRepeatable(String dateTimeStr) {
        if (dateTimeStr != null && dateTimeStr.startsWith("R")) {
            return true;
        }
        
        return false;
    }
    
    public static boolean isPeriod(String dateTimeStr) {
        if (dateTimeStr != null && dateTimeStr.startsWith("P")) {
            return true;
        }
        
        return false;
    }

    
    public static long parseDuration(String durationStr) {
        if (isPeriod(durationStr)) {
            Period p = ISOPeriodFormat.standard().parsePeriod(durationStr);
            return p.toStandardDuration().getMillis();
        } else {
            return TimeUtils.parseTimeString(durationStr);
        }
    }
    
    public static long parseDateAsDuration(String dateTimeStr) {
        try {
        
            DateTime dt = ISODateTimeFormat.dateTimeParser().parseDateTime(dateTimeStr);
            Duration duration = new Duration(System.currentTimeMillis(), dt.getMillis());
            
            return duration.getMillis();
        } catch (Exception e) {
            return TimeUtils.parseTimeString(dateTimeStr);
        }
    }
    
    public static long[] parseRepeatableDateTime(String dateTimeStr) {
        long[] result = new long[3];
        if (isRepeatable(dateTimeStr)) {
            String repeats = null;
            String delayIn = null;
            String periodIn = null;
            String[] elements = dateTimeStr.split("/");
            if (elements.length==3) {
                repeats = elements[0].substring(1);
                delayIn = elements[1];
                periodIn = elements[2];
            } else {
                repeats = elements[0].substring(1);
                delayIn = new DateTime().toString();
                periodIn = elements[1];
            }
            DateTime startAtDelay =  ISODateTimeFormat.dateTimeParser().parseDateTime(delayIn);
            Duration startAtDelayDur = new Duration(System.currentTimeMillis(), startAtDelay.getMillis());
            Period period = ISOPeriodFormat.standard().parsePeriod(periodIn);
            result[0] = Long.parseLong(repeats.length()==0?"-1":repeats);
            result[1] = startAtDelayDur.getMillis();
            result[2] = period.toStandardDuration().getMillis();
            
            return result;
        } else {
            result = new long[]{TimeUtils.parseTimeString(dateTimeStr)};
            return result;
        }
    }
    
}
