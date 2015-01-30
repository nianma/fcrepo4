/**
 * Copyright 2015 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.kernel.utils.iterators;

import static com.google.common.base.Objects.equal;
import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Iterators.advance;
import static com.google.common.collect.Iterators.singletonIterator;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.hp.hpl.jena.rdf.model.ModelFactory.createDefaultModel;
import static java.util.Objects.hash;
import static java.util.Optional.empty;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.doubleStream;
import static java.util.stream.StreamSupport.intStream;
import static java.util.stream.StreamSupport.longStream;
import static java.util.stream.StreamSupport.stream;
import static org.fcrepo.kernel.utils.GuavaConversions.guavaFunction;
import static org.fcrepo.kernel.utils.GuavaConversions.guavaPredicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.jcr.Session;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ForwardingIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Ordering;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Statement;

/**
 * A stream of RDF triples along with some useful context.
 *
 * @author ajs6f
 * @since Oct 9, 2013
 */
public class RdfStream extends ForwardingIterator<Triple> implements Stream<Triple> {

    private final Map<String, String> namespaces = new HashMap<>();

    protected Iterator<Triple> triples;

    protected Session context;

    protected Node topic;

    protected List<Runnable> closers = new ArrayList<>();

    private static final Triple[] NONE = new Triple[] {};

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    public <Tr extends Triple, T extends Iterator<Tr>> RdfStream(final T triples) {
        super();
        this.triples = Iterators.transform(triples, cast());
    }

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    public <Tr extends Triple, T extends Iterable<Tr>> RdfStream(final T triples) {
        this(triples.iterator());
    }

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    public <Tr extends Triple, T extends Collection<Tr>> RdfStream(
            final T triples) {
        this(triples.iterator());
    }

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    @SafeVarargs
    public <T extends Triple> RdfStream(final T... triples) {
        this(Iterators.forArray(triples));
    }

    /**
     * Constructor that begins the stream with proffered statements.
     *
     * @param statements
     */
    @SafeVarargs
    public <T extends Statement> RdfStream(final T... statements) {
        this(Iterators.transform(Iterators.forArray(statements),
                statement2triple));
    }

    /**
     * Constructor that begins the stream with proffered triple.
     *
     * @param triple
     */
    public <T extends Triple> RdfStream(final T triple) {
        this(Iterators.forArray(new Triple[] { triple }));
    }

    /**
     * Constructor that begins the stream without any triples.
     */
    public RdfStream() {
        this(NONE);
    }

    /**
     * Returns the proffered {@link Triple}s with the context of this RdfStream.
     *
     * @param stream
     * @return proffered Triples with the context of this RDFStream
     */
    public <Tr extends Triple, T extends Iterator<Tr>> RdfStream withThisContext(final T stream) {
        return new RdfStream(stream).namespaces(namespaces()).topic(topic());
    }

    /**
     * Returns a new empty {@link RdfStream} with the context of this RdfStream.
     *
     * @return an empty RdfStream with the context of this RDFStream
     */
    public RdfStream withThisContext() {
        return new RdfStream().namespaces(namespaces()).topic(topic());
    }

    /**
     * Returns the proffered {@link Triple}s with the context of this RdfStream.
     *
     * @param stream
     * @return proffered Triples with the context of this RDFStream
     */
    public <Tr extends Triple, T extends Iterable<Tr>> RdfStream withThisContext(final T stream) {
        return new RdfStream(stream).namespaces(namespaces()).topic(topic());
    }

    /**
     * @param newTriples Triples to add.
     * @return This object for continued use.
     */
    public RdfStream concat(final Iterator<? extends Triple> newTriples) {
        triples = Iterators.concat(triples, newTriples);
        return this;
    }

    /**
     * @param other stream to add.
     * @return This object for continued use.
     */
    public RdfStream concat(final RdfStream other) {
        triples = Iterators.concat(triples, other);
        namespaces(other.namespaces());
        return this;
    }

    /**
     * @param newTriples Triples to add.
     * @return This object for continued use.
     */
    public RdfStream concat(final Stream<? extends Triple> newTriples) {
        triples = Iterators.concat(triples, newTriples.iterator());
        return this;
    }

    /**
     * @param newTriple Triples to add.
     * @return This object for continued use.
     */
    public <T extends Triple> RdfStream concat(final T newTriple) {
        triples = Iterators.concat(triples, singletonIterator(newTriple));
        return this;
    }

    /**
     * @param newTriples Triples to add.
     * @return This object for continued use.
     */
    public <T extends Triple> RdfStream concat(@SuppressWarnings("unchecked") final T... newTriples) {
        triples = Iterators.concat(triples, Iterators.forArray(newTriples));
        return this;
    }

    /**
     * @param newTriples Triples to add.
     * @return This object for continued use.
     */
    public RdfStream concat(final Collection<? extends Triple> newTriples) {
        triples = Iterators.concat(triples, newTriples.iterator());
        return this;
    }

    /**
     * As {@link Iterators#filter(Iterator, Predicate)} while maintaining context.
     *
     * @param predicate
     * @return RdfStream
     */
    public RdfStream filter(final Predicate<? super Triple> predicate) {
        return withThisContext(Iterators.filter(this, predicate));
    }

    /**
     * As {@link Iterators#transform(Iterator, Function)}.
     *
     * @param f
     * @return Iterator
     */
    public <ToType> Iterator<ToType> transform(final Function<? super Triple, ToType> f) {
        return Iterators.transform(this, f);
    }

    /**
     * RdfStream
     *
     * @param prefix
     * @param uri
     * @return This object for continued use.
     */
    public RdfStream namespace(final String prefix, final String uri) {
        namespaces.put(prefix, uri);
        return this;
    }

    /**
     * @param nses
     * @return This object for continued use.
     */
    public RdfStream namespaces(final Map<String, String> nses) {
        namespaces.putAll(nses);
        return this;
    }

    /**
     * @return The {@link Session} in context
     */
    public Session session() {
        return this.context;
    }

    /**
     * Sets the JCR context of this stream
     *
     * @param session The {@link Session} in context
     */
    public RdfStream session(final Session session) {
        this.context = session;
        return this;
    }

    /**
     * @return The {@link Node} topic in context
     */
    public Node topic() {
        return this.topic;
    }

    /**
     * Sets the topic of this stream
     *
     * @param topic The {@link Node} topic in context
     */
    public RdfStream topic(final Node topic) {
        this.topic = topic;
        return this;
    }

    /**
     * WARNING! This method exhausts the RdfStream on which it is called!
     *
     * @return A {@link Model} containing the prefix mappings and triples in this stream of RDF
     */
    public Model asModel() {
        final Model model = createDefaultModel();
        model.setNsPrefixes(namespaces());
        while (hasNext()) {
            model.add(model.asStatement(next()));
        }
        return model;
    }

    /**
     * @param model A {@link Model} containing the prefix mappings and triples to be put into this stream of RDF
     * @return RDFStream
     */
    public static RdfStream fromModel(final Model model) {
        final Iterator<Triple> triples = Iterators.transform(model.listStatements(), statement2triple);
        return new RdfStream(triples).namespaces(model.getNsPrefixMap());
    }

    private static Function<Statement, Triple> statement2triple = new Function<Statement, Triple>() {

        @Override
        public Triple apply(final Statement s) {
            return s.asTriple();
        }

    };

    @Override
    protected Iterator<Triple> delegate() {
        return triples;
    }



    /**
     * @return Namespaces in scope for this stream.
     */
    public Map<String, String> namespaces() {
        return namespaces;
    }

    private static <T extends Triple> Function<T, Triple> cast() {
        return new Function<T, Triple>() {

            @Override
            public Triple apply(final T prototriple) {
                return prototriple;
            }

        };
    }

    /*
     * We ignore duplicated triples for equality. (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        if (!(o instanceof RdfStream)) {
            return false;
        }
        final RdfStream rdfo = (RdfStream) o;

        final boolean triplesEqual =
                equal(copyOf(rdfo.triples), copyOf(this.triples));

        final boolean namespaceMappingsEqual =
                equal(rdfo.namespaces(), this.namespaces());

        final boolean topicEqual =
                equal(rdfo.topic(), this.topic());

        return triplesEqual && namespaceMappingsEqual && topicEqual;

    }

    @Override
    public int hashCode() {
        return hash(namespaces(), triples, topic());
    }

    @Override
    public RdfStream iterator() {
        return this;
    }

    @Override
    public Spliterator<Triple> spliterator() {
        return spliteratorUnknownSize(this, 0);
    }

    @Override
    public boolean isParallel() {
        // TODO determine parallelism
        return false;
    }

    @Override
    public RdfStream sequential() {
        return this;
    }

    @Override
    public RdfStream parallel() {
        // TODO impl parallelism
        throw new UnsupportedOperationException();
    }

    @Override
    public RdfStream unordered() {
        return this;
    }

    @Override
    public RdfStream onClose(final Runnable closeHandler) {
        closers.add(closeHandler);
        return this;
    }

    @Override
    public void close() {
        closers.forEach(Runnable::run);
    }

    @Override
    public Stream<Triple> filter(final java.util.function.Predicate<? super Triple> predicate) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <R> Stream<R> map(final java.util.function.Function<? super Triple, ? extends R> f) {
        return streamIterator(transform(guavaFunction(f)));
    }

    private static <R> Stream<R> streamIterator(final Iterator<? extends R> transformedTriples) {
        return stream(split(transformedTriples), false);
    }

    private static <R> Spliterator<R> split(final Iterator<? extends R> transformedTriples) {
        return spliteratorUnknownSize(transformedTriples,0);
    }

    @Override
    public IntStream mapToInt(final ToIntFunction<? super Triple> f) {
        final PrimitiveIterator.OfInt ints = new PrimitiveIterator.OfInt() {

            @Override
            public boolean hasNext() {
                return triples.hasNext();
            }

            @Override
            public int nextInt() {
                return f.applyAsInt(triples.next());
            }
        };
        return intStream(spliteratorUnknownSize(ints, 0), false);
    }

    @Override
    public LongStream mapToLong(final ToLongFunction<? super Triple> f) {
        final PrimitiveIterator.OfLong longs = new PrimitiveIterator.OfLong() {

            @Override
            public boolean hasNext() {
                return triples.hasNext();
            }

            @Override
            public long nextLong() {
                return f.applyAsLong(triples.next());
            }
        };
        return longStream(spliteratorUnknownSize(longs, 0), false);
    }

    @Override
    public DoubleStream mapToDouble(final ToDoubleFunction<? super Triple> f) {
        final PrimitiveIterator.OfDouble longs = new PrimitiveIterator.OfDouble() {

            @Override
            public boolean hasNext() {
                return triples.hasNext();
            }

            @Override
            public double nextDouble() {
                return f.applyAsDouble(triples.next());
            }
        };
        return doubleStream(spliteratorUnknownSize(longs, 0), false);
    }

    @Override
    public <R> Stream<R> flatMap(final java.util.function.Function<? super Triple, ? extends Stream<? extends R>> mapper) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IntStream flatMapToInt(final java.util.function.Function<? super Triple, ? extends IntStream> mapper) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public LongStream flatMapToLong(final java.util.function.Function<? super Triple, ? extends LongStream> mapper) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DoubleStream flatMapToDouble(final java.util.function.Function<? super Triple, ? extends DoubleStream> mapper) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Requires drawing all distinct triples into heap.
     *
     * @see java.util.stream.Stream#distinct()
     */
    @Override
    public RdfStream distinct() {
        return withThisContext(newHashSet(triples));
    }

    @Override
    public RdfStream sorted() {
        throw new UnsupportedOperationException("There is no natural ordering on RDF triples!");
    }

    /** Requires drawing all triples into heap.
     * @see java.util.stream.Stream#sorted(java.util.Comparator)
     */
    @Override
    public RdfStream sorted(final Comparator<? super Triple> comparator) {
        final TreeSet<Triple> sortedTriples = new TreeSet<>(comparator);
        sortedTriples.addAll(newArrayList(triples));
        return withThisContext(sortedTriples);
    }

    @Override
    public Stream<Triple> peek(final Consumer<? super Triple> action) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RdfStream limit(final long maxSize) {
        return (maxSize == -1) ? this : withThisContext(Iterators.limit(triples, (int) maxSize));
    }

    @Override
    public RdfStream skip(final long n) {
        advance(triples, (int) n);
        return this;
    }

    @Override
    public void forEach(final Consumer<? super Triple> action) {
        // TODO impl parallelism
        forEachOrdered(action);
    }

    @Override
    public void forEachOrdered(final Consumer<? super Triple> action) {
        while (hasNext()) {
            action.accept(next());
        }
    }

    @Override
    public Triple[] toArray() {
        return Iterators.toArray(triples, Triple.class);
    }

    @Override
    public <A> A[] toArray(final IntFunction<A[]> generator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Triple reduce(final Triple identity, final BinaryOperator<Triple> accumulator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Optional<Triple> reduce(final BinaryOperator<Triple> accumulator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <U> U reduce(final U identity, final BiFunction<U, ? super Triple, U> accumulator, final BinaryOperator<U> combiner) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <R> R collect(final Supplier<R> supplier, final BiConsumer<R, ? super Triple> accumulator, final BiConsumer<R, R> combiner) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <R, A> R collect(final Collector<? super Triple, A, R> collector) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Optional<Triple> min(final Comparator<? super Triple> comparator) {
        try {
            return Optional.of(Ordering.from(comparator).min(triples));
        } catch (final NoSuchElementException e) {
            return empty();
        }
    }

    @Override
    public Optional<Triple> max(final Comparator<? super Triple> comparator) {
        try {
            return Optional.of(Ordering.from(comparator).max(triples));
        } catch (final NoSuchElementException e) {
            return empty();
        }
    }

    @Override
    public long count() {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean anyMatch(final java.util.function.Predicate<? super Triple> p) {
        return Iterators.any(triples, guavaPredicate(p));
    }

    @Override
    public boolean allMatch(final java.util.function.Predicate<? super Triple> p) {
        return Iterators.all(triples, guavaPredicate(p));
    }

    @Override
    public boolean noneMatch(final java.util.function.Predicate<? super Triple> p) {
        return !anyMatch(p);
    }

    @Override
    public Optional<Triple> findFirst() {
        try {
            return Optional.of(triples.next());
        } catch (final NoSuchElementException e) {
            return empty();
        }
    }

    @Override
    public Optional<Triple> findAny() {
        return findFirst();
    }

}
