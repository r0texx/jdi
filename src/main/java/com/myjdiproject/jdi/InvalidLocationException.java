package com.myjdiproject.jdi;

import java.io.Serial;

public class InvalidLocationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -1928428056197269588L;

    public InvalidLocationException() {
        super();
    }

    public InvalidLocationException(String s) {
        super(s);
    }
}
