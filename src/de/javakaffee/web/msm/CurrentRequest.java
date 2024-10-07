package de.javakaffee.web.msm;

import org.apache.catalina.connector.Request;

public class CurrentRequest {

    private final InheritableThreadLocal<Request> _requestsThreadLocal = new InheritableThreadLocal<Request>();

    public Request get() {
        return _requestsThreadLocal.get();
    }

    public void set(final Request request) {
        _requestsThreadLocal.set(request);
    }

    public void reset() {
        _requestsThreadLocal.set(null);
    }

}
