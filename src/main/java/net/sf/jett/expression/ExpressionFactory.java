package net.sf.jett.expression;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlOperator;
import org.apache.commons.jexl3.internal.introspection.Uberspect;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.apache.commons.jexl3.introspection.JexlUberspect.JexlResolver;
import org.apache.commons.jexl3.introspection.JexlUberspect.PropertyResolver;
import org.apache.commons.logging.LogFactory;


/**
 * <p>An <code>ExpressionFactory</code> is a factory class that
 * creates and uses a <code>JexlEngine</code> to create JEXL
 * <code>Expressions</code>.</p>
 *
 * <p>It passes through several items to the JEXL Engine, including "silent"
 * and "lenient" flags, the JEXL parse cache size, and JEXL namespace function
 * objects (including jAgg functionality).</p>
 *
 * <p>As of 0.9.0, this class is no longer a singleton, to allow concurrent
 * <code>ExcelTransformers</code> to avoid contention by having their own
 * <code>ExpressionFactories</code>.
 *
 * @author Randy Gettman
 */
public class ExpressionFactory
{
    private JexlEngine myEngine;
    private Map<String, Object> myFuncs;
    private Map<String, JexlExpression> myExpressionCache;

    /**
     * Constructs a <code>ExpressionFactory</code>.  Initializes an internal
     * <code>JexlEngine</code> and initializes the functions map.
     */
    
    private JexlBuilder myBuilder;
    public ExpressionFactory()
    {
        myFuncs = new HashMap<>();
        myFuncs.put("jagg", JaggFuncs.class);
        myFuncs.put("jett", JettFuncs.class);
        myBuilder = new JexlBuilder().strict(true).silent(false).debug(false).namespaces(myFuncs);
        myEngine = myBuilder.create();
        myExpressionCache = new HashMap<>();
    }

    /**
     * Passes the given "lenient" flag on to the internal
     * <code>JexlEngine</code>.
     * @param lenient Whether the internal <code>JexlEngine</code> should be
     *    "lenient".
     */
    public void setLenient(boolean lenient)
    {
    	boolean strict = !lenient;
    	if (myEngine.isStrict() != strict) {
    		myBuilder.strict(strict);
            myEngine = myBuilder.create();
    	}
    }

    /**
     * Returns the internal <code>JexlEngine's</code> "lenient" flag.
     * @return Whether the internal <code>JexlEngine</code> is currently
     *    "lenient".
     */
    public boolean isLenient()
    {
        return !myEngine.isStrict();
    }

    /**
     * Passes the given "silent" flag on to the internal
     * <code>JexlEngine</code>.
     * @param silent Whether the internal <code>JexlEngine</code> should be
     *    "silent".
     */
    public void setSilent(boolean silent)
    {
    	if (myEngine.isSilent() != silent) {
    		myBuilder.silent(silent);
            myEngine = myBuilder.create();
    	}
    }

    public void permissions(JexlPermissions permissions)
    {
        final List<PropertyResolver> MAP = Collections.unmodifiableList(Arrays.asList(
		        JexlResolver.MAP,
		        JexlResolver.LIST,
		        JexlResolver.DUCK,
		        JexlResolver.PROPERTY,
		        JexlResolver.FIELD,
		        JexlResolver.CONTAINER
		));
        final List<PropertyResolver> POJO = Collections.unmodifiableList(Arrays.asList(
                JexlResolver.PROPERTY,
                JexlResolver.MAP,
                JexlResolver.LIST,
                JexlResolver.DUCK,
                JexlResolver.FIELD,
                JexlResolver.CONTAINER
        ));
        Uberspect a = new Uberspect(LogFactory.getLog(ExpressionFactory.class), (op, obj) -> {
        	  if (op == JexlOperator.ARRAY_GET) {
                  return MAP;
              }
              if (op == JexlOperator.ARRAY_SET) {
                  return MAP;
              }
              if (op == null && obj instanceof Map) {
                  return MAP;
              }
              return POJO;
        },permissions);
        myBuilder.uberspect(a);
        myEngine = myBuilder.create();
    }    

    /**
     * Returns the internal <code>JexlEngine's</code> "silent" flag.
     * @return Whether the internal <code>JexlEngine</code> is currently
     *    "silent".
     */
    public boolean isSilent()
    {
        return myEngine.isSilent();
    }

    /**
     * Sets the size of the Expression cache to be used inside the JEXL Engine.
     * @param size The size of the cache.
     * @since 0.2.0
     */
    public void setCache(int size)
    {
    	if (myBuilder.cache() != size) {
    		myBuilder.cache(size);
            myEngine = myBuilder.create();
    	}
    }

    /**
     * Passes the given "debug" flag on to the internal
     * <code>JexlEngine</code>.
     * @param debug Whether the internal <code>JexlEngine</code> should be
     *    in "debug" mode.
     * @since 0.9.1
     */
    public void setDebug(boolean debug)
    {
    	if (myEngine.isDebug() != debug) {
    		myBuilder.debug(debug);
            myEngine = myBuilder.create();
    	}
    }

    /**
     * Registers an object under the given namespace in the JEXL Engine.  Each
     * public method in the object's class is exposed as a "function" available
     * in the JEXL Engine.  To use instance methods, pass an instance of the
     * object.  To use class methods, pass a <code>Class</code> object.
     * @param namespace The namespace.
     * @param funcsObject An object (or a <code>Class</code>) containing the
     *    methods to expose as JEXL Engine functions.
     * @throws IllegalArgumentException If the namespace has already been
     *    registered.
     * @since 0.2.0
     */
    public void registerFuncs(String namespace, Object funcsObject)
    {
        if (myFuncs.get(namespace) != null) {
            throw new IllegalArgumentException("A functions object with namespace \"" +
                    namespace + "\" has already been registered.");
        }
        myFuncs.put(namespace, funcsObject);
    }

    /**
     * Create a JEXL <code>Expression</code> from a string.
     * @param expression The expression as a <code>String</code>.
     * @return A JEXL <code>Expression</code>.
     */
    public JexlExpression createExpression(String expression)
    {
    	JexlExpression jexlExpr = myExpressionCache.get(expression);
        if (jexlExpr == null)
        {
            jexlExpr = myEngine.createExpression(expression);
            myExpressionCache.put(expression, jexlExpr);
        }
        return jexlExpr;
    }
}

