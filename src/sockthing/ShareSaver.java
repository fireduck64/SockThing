package sockthing;

import com.google.bitcoin.core.Sha256Hash;


/**
 * This interface is for saving credit for a user after the worker submits a work unit
 */
public interface ShareSaver
{
    public void saveShare(PoolUser pu, JobInfo ji, SubmitResult submit_result, String source) throws ShareSaveException;

}

