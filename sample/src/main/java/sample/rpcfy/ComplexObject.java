package sample.rpcfy;

public class ComplexObject {
    public String name;
    public SEX sex;
    public Family family;


    public enum SEX {
        MALE,
        FEMALE
    }

    public static class Family {
        public String familyName;
    }

}
