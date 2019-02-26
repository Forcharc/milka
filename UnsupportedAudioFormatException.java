package milka.audio;

public class UnsupportedAudioFormatException extends Exception {

    public UnsupportedAudioFormatException() {
    }

    public UnsupportedAudioFormatException(String message) {
        super(message);
    }

    public UnsupportedAudioFormatException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedAudioFormatException(Throwable cause) {
        super(cause);
    }

    public UnsupportedAudioFormatException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
    
}
