package trivia;

import java.util.Objects;

public class TriviaUser {

    private String name;

    private int result;

    public TriviaUser(String name) {
        this.name = name;
        this.result = 0;
    }

    public void increaseResult(){
        this.result += 1;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return name + ";" + result;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TriviaUser client = (TriviaUser) o;
        return result == client.result && Objects.equals(name, client.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, result);
    }


}
