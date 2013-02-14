package sockthing;


/**
 * If you have any other user settings, you can just extend this and add them on.
 * The same PoolUser from the AuthHandler will be sent into the ShareSaver. 
 */
public class PoolUser
{
    private String worker_name; //The name used to submit work units
    private String name; //Name that work is credited to
    private int difficulty = 1;

    public PoolUser(String worker_name)
    {
        this.worker_name =worker_name;
    }

    public void setName(String name)
    {
        this.name = name;
    }
    public void setDifficulty(int difficulty)
    {
        this.difficulty = difficulty;
    }

    public int getDifficulty(){return difficulty;}
    public String getName(){return name;}
    public String getWorkerName(){return worker_name;}

}
