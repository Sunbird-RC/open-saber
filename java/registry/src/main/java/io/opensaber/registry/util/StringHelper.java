package io.opensaber.registry.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * This class creates util methods for String modification and replacing
 */
public class StringHelper {

    private static final String SQUARE_BRACE_REGEX = "[\\s\\[\\]]";
    private static final String EMPTY = "";

    /**
     * This method checks the input String in array format and removes the characters "[", "]"
     *
     * @param input as any String content
     * @return string replaced with angle braces with empty character
     */
    public static String removeSquareBraces(String input) {
        Pattern pattern = Pattern.compile(SQUARE_BRACE_REGEX);
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll(EMPTY);
    }
}
