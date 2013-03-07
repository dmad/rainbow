package damd.rainbow.util.logging;

import java.util.Date;

import java.util.logging.LogRecord;
import java.util.logging.Formatter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class DevLogFormatter
    extends Formatter
{
    private static class Builder
    {
	private int width;
	private DateFormat date_format;

	private StringBuilder buffer;
	private int column;

	public Builder (int width, DateFormat date_format)
	{
	    if (width < 1)
		throw new IllegalArgumentException ("width(" + width + ") < 1");

	    if (null == date_format)
		throw new NullPointerException ("date_format");

	    this.width = width;
	    this.date_format = date_format;

	    buffer = new StringBuilder ();
	    column = 0;
	}

	private void newLine ()
	{
	    buffer.append ('\n');
	    column = 0;
	}

	private void append (char what)
	{
	    buffer.append (what);
	    ++column;
	}

	private void append (char what, int times)
	{
	    if (times > 0) {
		for (int i = 0;i < times;++i)
		    buffer.append (what);
		column += times;
	    }
	}

	private int appendUntilEndOfLine (String what, int from)
	{
	    int remaining = 0;

	    if (null != what) {
		int eol = what.indexOf ("\n", from);
		int chars, padding = 0;
		boolean skip_eol = false, unfinished;

		if (-1 == eol)
		    eol = what.length ();
		else
		    skip_eol = true;

		chars = eol - from;
		if (unfinished = (width - column < chars)) {
		    chars = width - column - 1;

		    for (int i = 1;i < chars - 10;++i)
			if (Character.isWhitespace
			    (what.charAt (from + chars - i))) {
			    chars -= i - 1;
			    padding = i - 1;
			    break;
			}
		}

		buffer.append (what.substring (from, from + chars));
		column += chars;

		append (' ', padding);

		if (unfinished) {
		    buffer.append ('\\');
		    ++column;
		}

		remaining = what.length () - from - chars
		    - (skip_eol && !unfinished ? 1 : 0);
	    }

	    return remaining;
	}

	public void startLine ()
	{
	    if (0 != column)
		newLine ();
	}

	public void finish ()
	{
	    startLine ();
	    append ('*', width);
	    newLine ();
	}

	public void append (String what, int indent)
	{
	    if (null != what) {
		final int length = what.length ();
		int remaining = length;

		while (remaining > 0) {
		    remaining = appendUntilEndOfLine (what,
						      length - remaining);
		    if (remaining > 0) {
			startLine ();
			append (' ', indent);
		    }
		}
	    }
	}

	public void append (String label, String value)
	{
	    startLine ();
	    append (label, 0);
	    append (value, null != label ? label.length () : 0);
	}

	public void append (LogRecord record)
	{
	    Throwable throwable = record.getThrown ();

	    startLine ();
	    append ("Sequence  : ",
		    Long.toString (record.getSequenceNumber ()));

	    startLine ();
	    append ("Level     : ", record.getLevel ().getName ());

	    startLine ();
	    append ("Logger    : ", record.getLoggerName ());

	    if (null != record.getSourceClassName ()) {
		startLine ();
		append ("Class     : ", record.getSourceClassName ());
	    }

	    if (null != record.getSourceMethodName ()) {
		startLine ();
		append ("Method    : ", record.getSourceMethodName ());
	    }

	    startLine ();
	    append ("Time      : ",
		    date_format.format (new Date(record.getMillis ())));

	    startLine ();
	    append ("Message   : ", record.getMessage ());

	    for (boolean causedby = false;null != throwable;causedby = true) {
		final String label = causedby ? "Caused by : " : "Throwable : ";
		Builder builder = new Builder (width - label.length (),
					       date_format);
		StringBuilder trace = new StringBuilder ();

		builder.startLine ();
		builder.append ("Kind       : ",
				throwable.getClass ().getName ());

		if (null != throwable.getMessage ()) {
		    builder.startLine ();
		    builder.append ("Message    : ", throwable.getMessage ());
		}

		for (StackTraceElement e : throwable.getStackTrace ()) {
		    trace.append (e.toString ());
		    trace.append ('\n');
		}

		builder.startLine ();
		builder.append ("StackTrace : ", trace.toString ());

		builder.finish ();

		startLine ();
		append (label, builder.toString ());

		throwable = throwable.getCause ();
	    }
	}

	public String toString ()
	{
	    return buffer.toString ();
	}
    }

    private DateFormat date_format;

    public DevLogFormatter ()
    {
	date_format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSS");
    }

    // >>> Formatter

    public String format (LogRecord record)
    {
	Builder builder = new Builder (80, date_format);

	builder.append (record);
	builder.finish ();

	return builder.toString ();
    }

    // <<< Formatter
}
