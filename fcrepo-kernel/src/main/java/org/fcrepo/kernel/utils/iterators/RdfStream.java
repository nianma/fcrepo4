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

import static com.google.common.collect.Sets.newHashSet;
import static com.hp.hpl.jena.graph.Node.ANY;
import static com.hp.hpl.jena.rdf.model.ModelFactory.createDefaultModel;
import static java.util.Arrays.stream;
import static java.util.Objects.hash;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
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

import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

/**
 * A stream of RDF triples along with some useful context.
 *
 * @author ajs6f
 * @since Oct 9, 2013
 */
public class RdfStream implements Stream<Triple> {

    private final Map<String, String> namespaces = new HashMap<>();

    protected Stream<? extends Triple> triples;

    protected Session context;

    protected Node topic;

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    public <Tr extends Triple, T extends Stream<Tr>> RdfStream(final T triples) {
        this.triples = triples;
    }

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    public <Tr extends Triple, T extends Spliterator<Tr>> RdfStream(final T triples) {
        this(stream(() -> triples, triples.characteristics(), true));
    }

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    public <Tr extends Triple, T extends Iterator<Tr>> RdfStream(final T triples) {
        this(spliteratorUnknownSize(triples, 0));
    }

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    public <Tr extends Triple, T extends Iterable<Tr>> RdfStream(final T triples) {
        this(triples.spliterator());
    }

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    public <Tr extends Triple, T extends Collection<Tr>> RdfStream(final T triples) {
        this(triples.stream());
    }

    /**
     * Constructor that begins the stream with proffered triples.
     *
     * @param triples
     */
    @SafeVarargs
    public <T extends Triple> RdfStream(final T... triples) {
        this(stream(triples));
    }


    /**
     * Returns a new {@link RdfStream} with proffered triples and the context of this RdfStream.
     *
     * @param triples
     * @return an RdfStream with the context of this RDFStream
     */
    public <T extends Triple> RdfStream withThisContext(@SuppressWarnings("unchecked") final T... triples) {
        return new RdfStream(triples).namespaces(namespaces()).topic(topic()).session(session());
    }

    /**
     * Returns the proffered {@link Triple}s with the context of this RdfStream.
     *
     * @param stream
     * @return proffered Triples with the context of this RDFStream
     */
    public <Tr extends Triple, T extends Spliterator<Tr>> RdfStream withThisContext(final T stream) {
        return new RdfStream(stream).namespaces(namespaces()).topic(topic()).session(session());
    }

    /**
     * Returns the proffered {@link Triple}s with the context of this RdfStream.
     *
     * @param stream
     * @return proffered Triples with the context of this RDFStream
     */
    public <Tr extends Triple, T extends Iterator<Tr>> RdfStream withThisContext(final T stream) {
        return new RdfStream(stream).namespaces(namespaces()).topic(topic()).session(session());
    }


    /**
     * Returns the proffered {@link Triple}s with the context of this RdfStream.
     *
     * @param stream
     * @return proffered Triples with the context of this RDFStream
     */
    public <Tr extends Triple, T extends Iterable<Tr>> RdfStream withThisContext(final T stream) {
        return new RdfStream(stream).namespaces(namespaces()).topic(topic()).session(session());
    }

    /**
     * Returns the proffered {@link Triple}s with the context of this RdfStream.
     *
     * @param stream
     * @return proffered Triples with the context of this RDFStream
     */
    public <Tr extends Triple, T extends Stream<Tr>> RdfStream withThisContext(final T stream) {
        return new RdfStream(stream).namespaces(namespaces()).topic(topic()).session(session());
    }

    /**
     * @param newTriples Triples to add.
     * @return This object for continued use.
     */
    public RdfStream concat(final Iterator<? extends Triple> newTriples) {
        return concat(new RdfStream(newTriples));
    }

    /**
     * @param other stream to add.
     * @return This object for continued use.
     */
    public RdfStream concat(final RdfStream other) {
        namespaces(other.namespaces());
        return concat((Stream<? extends Triple>) other);
    }

    /**
     * @param newTriples Triples to add.
     * @return This object for continued use.
     */
    public RdfStream concat(final Stream<? extends Triple> newTriples) {
        triples = Stream.concat(triples, newTriples);
        return this;
    }


    /**
     * @param newTriples Triples to add.
     * @return This object for continued use.
     */
    public <T extends Triple> RdfStream concat(@SuppressWarnings("unchecked") final T... newTriples) {
        return concat(stream(newTriples));
    }

    /**
     * @param newTriples Triples to add.
     * @return This object for continued use.
     */
    public RdfStream concat(final Collection<? extends Triple> newTriples) {
        return concat(newTriples.stream());
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
     * <p>
     * This is a <a href="java.util.stream.Stream">terminal operation</a>.
     *
     * @return A {@link Model} containing the prefix mappings and triples in this stream of RDF
     */
    public Model asModel() {
        final Model model = createDefaultModel();
        model.setNsPrefixes(namespaces());
        triples.forEach(t -> model.add(model.asStatement(t)));
        return model;
    }

    /**
     * @param model A {@link Model} containing the prefix mappings and triples to be put into this stream of RDF
     * @return RDFStream
     */
    public static RdfStream fromModel(final Model model) {
        final ExtendedIterator<Triple> modelTriples = model.getGraph().find(ANY, ANY, ANY);
        return new RdfStream(modelTriples).namespaces(model.getNsPrefixMap());
    }

    /**
     * @return Namespaces in scope for this stream.
     */
    public Map<String, String> namespaces() {
        return namespaces;
    }

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
        if (!Objects.equals(rdfo.topic(), topic())) {
            return false;
        }
        if (!Objects.equals(rdfo.namespaces(), namespaces())) {
            return false;
        }
        if (!Objects.equals(rdfo.session(), session())) {
            return false;
        }
        final HashSet<? extends Triple> myTriples = newHashSet(triples.iterator());
        final HashSet<? extends Triple> theirTriples = newHashSet(rdfo.triples.iterator());
        if (!myTriples.equals(theirTriples)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hash(namespaces(), triples, topic(), session());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Triple> iterator() {
        return (Iterator<Triple>) triples.iterator();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Spliterator<Triple> spliterator() {
        return (Spliterator<Triple>) triples.spliterator();
    }

    @Override
    public boolean isParallel() {
        return triples.isParallel();
    }

    @Override
    public RdfStream sequential() {
        return withThisContext(triples.sequential());
    }

    @Override
    public RdfStream parallel() {
        return withThisContext(triples.parallel());
    }

    @Override
    public RdfStream unordered() {
        return withThisContext(triples.unordered());
    }

    @Override
    public RdfStream onClose(final Runnable closeHandler) {
        return withThisContext(triples.onClose(closeHandler));
    }

    @Override
    public void close() {
        triples.close();
    }

    @Override
    public RdfStream filter(final java.util.function.Predicate<? super Triple> p) {
        return withThisContext(triples.filter(p));
    }

    @Override
    public <R> Stream<R> map(final java.util.function.Function<? super Triple, ? extends R> f) {
        return triples.map(f);
    }

    @Override
    public IntStream mapToInt(final ToIntFunction<? super Triple> f) {
        return triples.mapToInt(f);
    }

    @Override
    public LongStream mapToLong(final ToLongFunction<? super Triple> f) {
        return triples.mapToLong(f);
    }

    @Override
    public DoubleStream mapToDouble(final ToDoubleFunction<? super Triple> f) {
        return triples.mapToDouble(f);
    }

    @Override
    public <R> Stream<R> flatMap(final java.util.function.Function<? super Triple, ? extends Stream<? extends R>> f) {
        return triples.flatMap(f);
    }

    @Override
    public IntStream flatMapToInt(final java.util.function.Function<? super Triple, ? extends IntStream> f) {
        return triples.flatMapToInt(f);
    }

    @Override
    public LongStream flatMapToLong(final java.util.function.Function<? super Triple, ? extends LongStream> f) {
        return triples.flatMapToLong(f);
    }

    @Override
    public DoubleStream flatMapToDouble(final java.util.function.Function<? super Triple, ? extends DoubleStream> f) {
        return triples.flatMapToDouble(f);
    }

    @Override
    public RdfStream distinct() {
        return withThisContext(triples.distinct());
    }

    @Override
    public RdfStream sorted() {
        return withThisContext(triples.sorted());
    }

    @Override
    public RdfStream sorted(final Comparator<? super Triple> comparator) {
        return withThisContext(triples.sorted(comparator));
    }

    @Override
    public RdfStream peek(final Consumer<? super Triple> action) {
        return withThisContext(triples.peek(action));
    }

    @Override
    public RdfStream limit(final long maxSize) {
        return withThisContext(triples.limit(maxSize));
    }

    @Override
    public RdfStream skip(final long n) {
        return withThisContext(triples.skip(n));
    }

    @Override
    public void forEach(final Consumer<? super Triple> action) {
        triples.forEach(action);
    }

    @Override
    public void forEachOrdered(final Consumer<? super Triple> action) {
        triples.forEachOrdered(action);
    }

    @Override
    public Triple[] toArray() {
        return (Triple[]) triples.toArray();
    }

    @Override
    public <A> A[] toArray(final IntFunction<A[]> generator) {
        return triples.toArray(generator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Triple reduce(final Triple identity, final BinaryOperator<Triple> accumulator) {
        return ((Stream<Triple>) triples).reduce(identity, accumulator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Triple> reduce(final BinaryOperator<Triple> accumulator) {
        return ((Stream<Triple>) triples).reduce(accumulator);
    }

    @Override
    public <U> U reduce(final U identity, final BiFunction<U, ? super Triple, U> accumulator, final BinaryOperator<U> combiner) {
        return triples.reduce(identity, accumulator, combiner);
    }

    @Override
    public <R> R collect(final Supplier<R> supplier, final BiConsumer<R, ? super Triple> accumulator, final BiConsumer<R, R> combiner) {
        return triples.collect(supplier, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(final Collector<? super Triple, A, R> collector) {
        return triples.collect(collector);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Triple> min(final Comparator<? super Triple> comparator) {
        return (Optional<Triple>) triples.min(comparator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Triple> max(final Comparator<? super Triple> comparator) {
        return (Optional<Triple>) triples.max(comparator);
    }

    @Override
    public long count() {
        return triples.count();
    }

    @Override
    public boolean anyMatch(final java.util.function.Predicate<? super Triple> p) {
        return triples.anyMatch(p);
    }

    @Override
    public boolean allMatch(final java.util.function.Predicate<? super Triple> p) {
        return triples.allMatch(p);
    }

    @Override
    public boolean noneMatch(final java.util.function.Predicate<? super Triple> p) {
        return triples.noneMatch(p);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Triple> findFirst() {
        return (Optional<Triple>) triples.findFirst();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Triple> findAny() {
        return (Optional<Triple>) triples.findAny();
    }
}
