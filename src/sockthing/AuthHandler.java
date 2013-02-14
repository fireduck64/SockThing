package sockthing;

public interface AuthHandler
{
    /**
     * Return PoolUser object if the user is legit.
     * Return null if the user is unknown/not allowed/incorrect
     */
    public PoolUser authenticate(String username, String password);

}
