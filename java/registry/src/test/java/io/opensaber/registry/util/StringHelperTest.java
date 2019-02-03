package io.opensaber.registry.util;

import java.util.*;
import java.util.regex.*;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.test.context.junit4.*;

import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
public class StringHelperTest {

    @Test
    public void testRemoveSquareBraces() {
        String expectedString = " hari,sri ram,giri";
        String actualString = StringHelper.removeSquareBraces("[ hari,sri ram,giri]");
        assertTrue(expectedString.equalsIgnoreCase(actualString));
    }

    @Test(expected = NullPointerException.class)
    public void testRemoveSquareBracesWithNull() {
        String actualString = StringHelper.removeSquareBraces(null);
    }

    @Test
    public void testToString(){
        String expectedString = "[ hari,sri ram,giri]";
        List<String> inputLst = new ArrayList<>();
        inputLst.add(" hari");
        inputLst.add("sri ram");
        inputLst.add("giri");

        String actualString = StringHelper.toString(inputLst);
        assertTrue(expectedString.equalsIgnoreCase(actualString));

    }

    @Test(expected = NullPointerException.class)
    public void testToStringWithNull(){
       StringHelper.toString(null);
    }
}
