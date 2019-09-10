/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.event;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.FeatureState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.LinkWithRoleModel;

public class LinkFeatureDeletedEvent
{
    private final IPartialPageRequestHandler requestHandler;
    
    private final FeatureState fs;
    
    private final LinkWithRoleModel linkWithRoleModel;
    
    private final AjaxRequestTarget target;
    
    public LinkFeatureDeletedEvent(FeatureState aFs, AjaxRequestTarget aTarget,
            LinkWithRoleModel aLinkWithRoleModel, IPartialPageRequestHandler aRequestHandler)
    {
        fs = aFs;
        linkWithRoleModel = aLinkWithRoleModel;
        requestHandler = aRequestHandler;
        target = aTarget;
    }
    
    public FeatureState getFs()
    {
        return fs;
    }
    
    public LinkWithRoleModel getLinkWithRoleModel()
    {
        return linkWithRoleModel;
    }
    
    public IPartialPageRequestHandler getRequestHandler()
    {
        return requestHandler;
    }
    
    public AjaxRequestTarget getTarget()
    {
        return target;
    }
}
