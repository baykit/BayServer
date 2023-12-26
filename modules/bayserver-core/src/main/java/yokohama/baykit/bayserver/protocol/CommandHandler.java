package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.common.Reusable;

/**
 * base class for handling commands
 * (Uses visitor pattern)
 */
public interface CommandHandler<C extends Command<C, ?, ?, ?>> extends Reusable {

}
