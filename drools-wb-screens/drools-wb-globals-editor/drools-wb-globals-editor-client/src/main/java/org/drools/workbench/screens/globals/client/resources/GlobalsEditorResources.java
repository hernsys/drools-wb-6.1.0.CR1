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
package org.drools.workbench.screens.globals.client.resources;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import org.drools.workbench.screens.globals.client.resources.css.GlobalsEditorStylesCss;
import org.drools.workbench.screens.globals.client.resources.images.GlobalsEditorImageResources;

/**
 * Resources for Globals Editor
 */
public interface GlobalsEditorResources
        extends
        ClientBundle {

    public static final GlobalsEditorResources INSTANCE = GWT.create( GlobalsEditorResources.class );

    @Source("css/Styles.css")
    GlobalsEditorStylesCss CSS();

    GlobalsEditorImageResources images();

}
