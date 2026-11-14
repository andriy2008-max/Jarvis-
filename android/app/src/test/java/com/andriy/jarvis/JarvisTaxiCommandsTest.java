package com.andriy.jarvis;

public final class JarvisTaxiCommandsTest {
    private static void check(String input,String expected){
        String actual=JarvisTaxiCommands.destination(input);
        if(expected==null?actual!=null:!expected.equals(actual))
            throw new AssertionError(input+": expected "+expected+" got "+actual);
    }
    public static void main(String[] args){
        check("Таксі","");
        check("такси","");
        check("Джарвіс, таксі","");
        check("Виклич таксі до вокзалу","вокзалу");
        check("Замов таксі до Стебника","Стебника");
        check("Джарвіс, таксі до вокзалу","вокзалу");
        check("Джарвіс таксі","");
        check("Таксі Стебник дім","Стебник дім");
        check("Джарвіс, таксі Борислав дім","Борислав дім");
        check("Джарвіс таксі на роботу","роботу");
        check("таксі за адресою Київ Хрещатик 1","Київ Хрещатик 1");
        check("джарвіс таксі на вулицю Шевченка 20","вулицю Шевченка 20");
        check("відкрий Bolt",null);
        check(null,null);
        System.out.println("PASS JarvisTaxiCommandsTest");
    }
}
