package de.javakaffee.web.msm;

public class Pair<A,B> {

    private final A _first;
    private final B _second;

    public Pair(final A first, final B second) {
        super();
        _first = first;
        _second = second;
    }

    public static <A, B> Pair<A, B> of( final A first, final B second ) {
        return new Pair<A, B>( first, second );
    }

    public A getFirst() {
        return _first;
    }

    public B getSecond() {
        return _second;
    }

}
