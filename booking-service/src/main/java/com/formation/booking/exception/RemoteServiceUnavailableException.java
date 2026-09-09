package com.formation.booking.exception;

/**
 * Un service dont depend la saga est injoignable ou son circuit est ouvert. La saga s'arrete
 * sans effet de bord plutot que de laisser une reservation incoherente.
 */
public class RemoteServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    public RemoteServiceUnavailableException(String serviceName, Throwable cause) {
        super("Service " + serviceName + " indisponible : " + describe(cause), cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    private static String describe(Throwable cause) {
        if (cause == null) {
            return "cause inconnue";
        }
        return cause.getClass().getSimpleName()
                + (cause.getMessage() != null ? " - " + cause.getMessage() : "");
    }
}
