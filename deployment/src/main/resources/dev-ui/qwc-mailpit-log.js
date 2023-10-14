import { QwcServerLog} from 'qwc-server-log';

/**
 * This component filter the log to only show Mailpit related entries.
 */
export class QwcMailpitLog extends QwcServerLog {

    doLogEntry(entry){
        if (entry.loggerName && entry.loggerName.includes("MailpitContainer")) {
            return true;
        }
        return false;
    }
}

customElements.define('qwc-mailpit-log', QwcMailpitLog);