package org.example;

public class Main {
    public static void main(String[] args) throws Exception {
        Class.forName("test.testDAO")
                .getMethod("main", String[].class)
                .invoke(null, (Object) new String[]{});
    }
}