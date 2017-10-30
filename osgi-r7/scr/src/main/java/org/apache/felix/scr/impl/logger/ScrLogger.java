/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.impl.logger;

import java.io.PrintStream;
import java.text.MessageFormat;

import org.apache.felix.scr.impl.manager.ScrConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This is the "global" logger used by the implementation.
 *
 */
public class ScrLogger
{
    //  name of the LogService class (this is a string to not create a reference to the class)
    private static final String LOGSERVICE_CLASS = "org.osgi.service.log.LogService";

    // the log service to log messages to
    private final ServiceTracker<LogService, LogService> logServiceTracker;

    private final ScrConfiguration config;

    private final String bundleId;

    private static String getBundleIdentifier(final Bundle bundle)
    {
        final StringBuilder sb = new StringBuilder("bundle ");
        // symbolic name might be null
        if ( bundle.getSymbolicName() != null )
        {
            sb.append(bundle.getSymbolicName());
            sb.append(':');
            sb.append(bundle.getVersion());
            sb.append( " (" );
            sb.append( bundle.getBundleId() );
            sb.append( ")" );
        }
        else
        {
            sb.append( bundle.getBundleId() );
        }

        return sb.toString();
    }

    public ScrLogger(final BundleContext bundleContext, final ScrConfiguration config)
    {
        this.config = config;
        this.bundleId = getBundleIdentifier(bundleContext.getBundle());
        logServiceTracker = new ServiceTracker<>( bundleContext, LOGSERVICE_CLASS, null );
        logServiceTracker.open();
    }

    public void close()
    {
        logServiceTracker.close();
    }

    protected LogService getLogService()
    {
        return logServiceTracker.getService();
    }

    /**
     * Method to actually emit the log message. If the LogService is available,
     * the message will be logged through the LogService. Otherwise the message
     * is logged to stdout (or stderr in case of LOG_ERROR level messages),
     *
     * @param level The log level to log the message at
     * @param pattern The {@code java.text.MessageFormat} message format
     *      string for preparing the message
     * @param ex An optional <code>Throwable</code> whose stack trace is written,
     * @param arguments The format arguments for the <code>pattern</code>
     *      string.
     */
    public void log(final int level, final String pattern, final Throwable ex, final Object... arguments )
    {
        if ( isLogEnabled( level ) )
        {
            final String message;
            if ( arguments == null || arguments.length == 0 )
            {
                message = pattern;
            }
            else
            {
                for(int i=0;i<arguments.length;i++)
                {
                    if ( arguments[i] instanceof Bundle )
                    {
                        arguments[i] = getBundleIdentifier((Bundle)arguments[i]);
                    }
                }
                message = MessageFormat.format( pattern, arguments );
            }
            log( level, message, ex );
        }
    }

    /**
     * Returns {@code true} if logging for the given level is enabled.
     */
    public boolean isLogEnabled(final int level)
    {
        return config.getLogLevel() >= level;
    }

    /**
     * Method to actually emit the log message. If the LogService is available,
     * the message will be logged through the LogService. Otherwise the message
     * is logged to stdout (or stderr in case of LOG_ERROR level messages),
     *
     * @param level The log level of the messages. This corresponds to the log
     *          levels defined by the OSGi LogService.
     * @param message The message to print
     * @param ex The <code>Throwable</code> causing the message to be logged.
     */
    public void log(final int level, final String message, final Throwable ex)
    {
        if ( isLogEnabled( level ) )
        {
            final LogService logger = getLogService();
            if ( logger == null )
            {
                // output depending on level
                final PrintStream out = ( level == LogService.LOG_ERROR )? System.err: System.out;

                // level as a string
                final StringBuilder buf = new StringBuilder();
                switch (level)
                {
                    case ( LogService.LOG_DEBUG ):
                        buf.append( "DEBUG: " );
                        break;
                    case ( LogService.LOG_INFO ):
                        buf.append( "INFO : " );
                        break;
                    case ( LogService.LOG_WARNING ):
                        buf.append( "WARN : " );
                        break;
                    case ( LogService.LOG_ERROR ):
                        buf.append( "ERROR: " );
                        break;
                    default:
                        buf.append( "UNK  : " );
                        break;
                }

                // bundle information
                buf.append( this.bundleId );
                buf.append(" : ");
                buf.append(message);

                final String msg = buf.toString();

                if ( ex == null )
                {
                    out.println(msg);
                }
                else
                {
                    // keep the message and the stacktrace together
                    synchronized ( out )
                    {
                        out.println( msg );
                        ex.printStackTrace( out );
                    }
                }
            }
            else
            {
                logger.log( level, this.bundleId + " : " + message, ex );
            }
        }
    }

}