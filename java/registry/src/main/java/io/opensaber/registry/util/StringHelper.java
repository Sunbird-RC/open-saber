package io.opensaber.registry.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class creates util methods for String modification and replacing
 */
public class StringHelper {

    private static final String ANGLE_BRACE_REGEX = "[\\s\\[\\]]";
    private static final String EMPTY = "";
    private static Logger logger = LoggerFactory.getLogger(StringHelper.class);

    /**
     * This method checks the input String in array format and removes the characters "[", "]"
     *
     * @param input as any String content
     * @return string replaced with angle braces with empty character
     */
    public static String replaceAngleBraces(String input) {
        String updatedValue = input;
        if (!input.isEmpty()) {
            Pattern pattern = Pattern.compile(ANGLE_BRACE_REGEX);
            Matcher matcher = pattern.matcher(input);
            updatedValue =  matcher.replaceAll(EMPTY);
        } else {
            logger.error("Input String is empty");
        }
        return updatedValue;
    }
}
