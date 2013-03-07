package damd.rainbow.behavior;

public interface Engine
{
    public enum State
    {
	STOPPED, RUNNING;
    }

    public State currentState ();

    public void changeState (State new_state)
	throws Exception;
}
