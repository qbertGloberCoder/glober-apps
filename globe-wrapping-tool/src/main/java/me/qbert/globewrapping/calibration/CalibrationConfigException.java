package me.qbert.globewrapping.calibration;

/** Thrown when a calibration config file is malformed or missing required fields. */
public class CalibrationConfigException extends RuntimeException {

    public CalibrationConfigException(String message) {
        super(message);
    }

    public CalibrationConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
