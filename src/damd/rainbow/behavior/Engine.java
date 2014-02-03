package damd.rainbow.behavior;

public interface Engine
    extends Named
{
    public enum State
    {
	STOPPED, RUNNING;
    }

    public State currentState ();

    public void changeState (State new_state)
	throws Exception;
}
