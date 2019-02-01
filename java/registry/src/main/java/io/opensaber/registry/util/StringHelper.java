package io.opensaber.registry.util;

public class StringHelper {

    /** This method checks the input String in array format and removes the characters "[", "]"
     * @param input
     * @return
     */
    public static String modifyArrayFormat(String input) {
        String updatedValue = input;
        if(input.contains(" ")){
            updatedValue = input.replace(" ","").replace("[","").replace("]","");
        } else {
            updatedValue = input.replace("[","").replace("]","");
        }
        return updatedValue;
    }
}
