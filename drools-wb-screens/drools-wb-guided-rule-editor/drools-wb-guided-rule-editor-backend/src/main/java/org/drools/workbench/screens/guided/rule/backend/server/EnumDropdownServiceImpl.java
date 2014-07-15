/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.guided.rule.backend.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;

import org.drools.core.util.MVELSafeHelper;
import org.drools.workbench.screens.guided.rule.service.EnumDropdownService;
import org.guvnor.common.services.backend.exceptions.ExceptionUtilities;
import org.jboss.errai.bus.server.annotations.Service;
import org.mvel2.templates.TemplateRuntime;

@Service
@ApplicationScoped
public class EnumDropdownServiceImpl
        implements EnumDropdownService {

    @Override
    public String[] loadDropDownExpression( final String[] valuePairs,
                                            String expression ) {
        try {
            final Map<String, String> context = new HashMap<String, String>();

            for ( final String valuePair : valuePairs ) {
                if ( valuePair == null ) {
                    return new String[ 0 ];
                }
                final String[] pair = valuePair.split( "=" );
                context.put( pair[ 0 ],
                             pair[ 1 ] );
            }
            // first interpolate the pairs
            expression = (String) TemplateRuntime.eval( expression,
                                                        context );

            // now we can eval it for real...
            Object result = MVELSafeHelper.getEvaluator().eval( expression );
            if ( result instanceof String[] ) {
                return (String[]) result;
            } else if ( result instanceof List ) {
                List l = (List) result;
                String[] xs = new String[ l.size() ];
                for ( int i = 0; i < xs.length; i++ ) {
                    Object el = l.get( i );
                    xs[ i ] = el.toString();
                }
                return xs;
            } else {
                return null;
            }

        } catch ( Exception e ) {
            throw ExceptionUtilities.handleException( e );
        }
    }

}
