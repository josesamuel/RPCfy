package sample.rpcfy;

public class MyObj {

    private String name;
    private int age;

    public MyObj() {
    }

    public MyObj(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        System.out.println(1);
        return name ;
    }

    public int getAge() {
        return age;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "Name " + name + " Age " + age;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * age;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof MyObj) && ((MyObj) obj).name.equals(name) && ((MyObj) obj).age == age;
    }
}
