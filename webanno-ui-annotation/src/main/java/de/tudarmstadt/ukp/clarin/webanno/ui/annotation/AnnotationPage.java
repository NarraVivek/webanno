/*
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
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
package de.tudarmstadt.ukp.clarin.webanno.ui.annotation;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectByAddr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.wicketstuff.annotation.mount.MountPath;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.api.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorStateImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.SecurityUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotator;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentStateTransition;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.LambdaAjaxLink;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.AnnotationPreferencesModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.DocumentNamePanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.ExportModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.FinishImage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.FinishLink;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.GuidelineModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.detail.AnnotationDetailEditorPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.dialog.OpenDocumentDialog;
import de.tudarmstadt.ukp.clarin.webanno.webapp.core.app.ApplicationPageBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import wicket.contrib.input.events.EventType;
import wicket.contrib.input.events.InputBehavior;
import wicket.contrib.input.events.key.KeyType;

/**
 * A wicket page for the Brat Annotation/Visualization page. Included components for pagination,
 * annotation layer configuration, and Exporting document
 */
@MountPath("/annotation.html")
public class AnnotationPage
    extends ApplicationPageBase
{
    private static final Logger LOG = LoggerFactory.getLogger(AnnotationPage.class);

    private static final long serialVersionUID = 1378872465851908515L;

    @SpringBean(name = "documentRepository")
    private RepositoryService repository;

    @SpringBean(name = "annotationService")
    private AnnotationService annotationService;

    @SpringBean(name = "userRepository")
    private UserDao userRepository;

    private BratAnnotator annotator;

    private FinishImage finish;

    private NumberTextField<Integer> gotoPageTextField;
    private AnnotationDetailEditorPanel editor;

    private int gotoPageAddress;

    // Open the dialog window on first load
    private boolean firstLoad = true;

    private Label numberOfPages;
    private DocumentNamePanel documentNamePanel;

    private long currentprojectId;

    private int totalNumberOfSentence;

    private WebMarkupContainer sidebarCell;
    private WebMarkupContainer annotationViewCell;
    
    private ModalWindow openDocumentsModal;
    
    public AnnotationPage(final PageParameters aPageParameters)
    {
        setModel(Model.of(new AnnotatorStateImpl(Mode.ANNOTATION)));
        
        sidebarCell = new WebMarkupContainer("sidebarCell") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onComponentTag(ComponentTag aTag)
            {
                super.onComponentTag(aTag);
                AnnotatorState state = AnnotationPage.this.getModelObject();
                aTag.put("width", state.getPreferences().getSidebarSize()+"%");
            }
        };
        sidebarCell.setOutputMarkupId(true);
        add(sidebarCell);

        annotationViewCell = new WebMarkupContainer("annotationViewCell") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onComponentTag(ComponentTag aTag)
            {
                super.onComponentTag(aTag);
                AnnotatorState state = AnnotationPage.this.getModelObject();
                aTag.put("width", (100-state.getPreferences().getSidebarSize())+"%");
            }
        };
        annotationViewCell.setOutputMarkupId(true);
        add(annotationViewCell);

        editor = new AnnotationDetailEditorPanel("annotationDetailEditorPanel", getModel())
        {
            private static final long serialVersionUID = 2857345299480098279L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                aTarget.add(numberOfPages);

                try {
                    annotator.bratRender(aTarget, getCas());
                    annotator.bratSetHighlight(aTarget,
                            getModelObject().getSelection().getAnnotation());
                }
                catch (Exception e) {
                    LOG.info("Error reading CAS " + e.getMessage());
                    error("Error reading CAS " + e.getMessage());
                    return;
                }
            }

            @Override
            protected void onAutoForward(AjaxRequestTarget aTarget)
            {
                try {
                    annotator.bratRender(aTarget, getCas());
                }
                catch (Exception e) {
                    LOG.info("Error reading CAS " + e.getMessage());
                    error("Error reading CAS " + e.getMessage());
                    return;
                }
            }
        };
        editor.setOutputMarkupId(true);
        sidebarCell.add(editor);
        
        annotator = new BratAnnotator("embedder1", getModel(), editor);
        annotationViewCell.add(annotator);

        add(documentNamePanel = (DocumentNamePanel) new DocumentNamePanel("documentNamePanel",
                getModel()).setOutputMarkupId(true));

        numberOfPages = new Label("numberOfPages", new Model<String>());
        numberOfPages.setOutputMarkupId(true);
        add(numberOfPages);

        add(openDocumentsModal = new OpenDocumentDialog("openDocumentsModal", getModel()) {
            private static final long serialVersionUID = 5474030848589262638L;

            @Override
            public void onDocumentSelected(AjaxRequestTarget aTarget)
            {
                actionLoadDocument(aTarget);
                try {
                    editor.loadFeatureEditorModels(aTarget);
                }
                catch (AnnotationException e) {
                    error("Error loading layers" + e.getMessage());
                }
            }
        });

        add(new AnnotationPreferencesModalPanel("annotationLayersModalPanel", getModel(), editor)
        {
            private static final long serialVersionUID = -4657965743173979437L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                try {
                    AnnotatorState state = AnnotationPage.this.getModelObject();
                    
                    JCas jCas = getJCas();
                    
                    // The number of visible sentences may have changed - let the state recalculate 
                    // the visible sentences 
                    Sentence sentence = selectByAddr(jCas, Sentence.class,
                            state.getFirstVisibleSentenceAddress());
                    state.setFirstVisibleSentence(sentence);
                    
                    updateSentenceAddress(jCas, aTarget);
                    
                    // Re-render the whole page because the width of the sidebar may have changed
                    aTarget.add(AnnotationPage.this);
                }
                catch (Exception e) {
                    LOG.info("Error reading CAS " + e.getMessage());
                    error("Error reading CAS " + e.getMessage());
                    return;
                }
            }
        });

        add(new ExportModalPanel("exportModalPanel", getModel()){
            private static final long serialVersionUID = -468896211970839443L;
            
            {
                setOutputMarkupId(true);
                setOutputMarkupPlaceholderTag(true);
            }
            
            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                AnnotatorState state = AnnotationPage.this.getModelObject();
                setVisible(state.getProject() != null
                        && (SecurityUtil.isAdmin(state.getProject(), repository, state.getUser())
                                || !state.getProject().isDisableExport()));
            }
        });

        add(new LambdaAjaxLink("showOpenDocumentModal", this::actionOpenDocument));

        add(new LambdaAjaxLink("showPreviousDocument", this::actionShowPreviousDocument)
                .add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_up },
                        EventType.click)));

        add(new LambdaAjaxLink("showNextDocument", this::actionShowNextDocument)
                .add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_down },
                        EventType.click)));

        add(new LambdaAjaxLink("showNext", this::actionShowNextPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Page_down }, EventType.click)));

        add(new LambdaAjaxLink("showPrevious", this::actionShowPreviousPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Page_up }, EventType.click)));

        add(new LambdaAjaxLink("showFirst", this::actionShowFirstPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Home }, EventType.click)));

        add(new LambdaAjaxLink("showLast", this::actionShowLastPage)
                .add(new InputBehavior(new KeyType[] { KeyType.End }, EventType.click)));

        add(new LambdaAjaxLink("gotoPageLink", this::actionGotoPage));

        add(new LambdaAjaxLink("toggleScriptDirection", this::actionToggleScriptDirection));
        
        add(new GuidelineModalPanel("guidelineModalPanel", getModel()));

        gotoPageTextField = (NumberTextField<Integer>) new NumberTextField<Integer>("gotoPageText",
                new Model<Integer>(0));
        Form<Void> gotoPageTextFieldForm = new Form<Void>("gotoPageTextFieldForm");
        gotoPageTextFieldForm.add(new AjaxFormSubmitBehavior(gotoPageTextFieldForm, "submit")
        {
            private static final long serialVersionUID = -1L;

            @Override
            protected void onSubmit(AjaxRequestTarget aTarget)
            {
                actionEnterPageNumer(aTarget);
            }
        });
        gotoPageTextField.setType(Integer.class);
        gotoPageTextField.setMinimum(1);
        gotoPageTextField.setDefaultModelObject(1);
        add(gotoPageTextFieldForm.add(gotoPageTextField));

        gotoPageTextField.add(new AjaxFormComponentUpdatingBehavior("change")
        {
            private static final long serialVersionUID = 56637289242712170L;

            @Override
            protected void onUpdate(AjaxRequestTarget aTarget)
            {
                try {
                    if (gotoPageTextField.getModelObject() < 1) {
                        aTarget.appendJavaScript("alert('Page number shouldn't be less than 1')");
                    }
                    else {
                        updateSentenceAddress(getJCas(), aTarget);
                    }
                }
                catch (Exception e) {
                    error(e.getMessage());
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                }
            }
        });

        finish = new FinishImage("finishImage", getModel());
        finish.setOutputMarkupId(true);

        add(new FinishLink("showYesNoModalPanel", getModel(), finish)
        {
            private static final long serialVersionUID = -4657965743173979437L;
            
            @Override
            public void onClose(AjaxRequestTarget aTarget)
            {
                super.onClose(aTarget);
                aTarget.add(editor);
            }
        });
    }
    
    public void setModel(IModel<AnnotatorState> aModel)
    {
        setDefaultModel(aModel);
    }
    
    @SuppressWarnings("unchecked")
    public IModel<AnnotatorState> getModel()
    {
        return (IModel<AnnotatorState>) getDefaultModel();
    }

    public void setModelObject(AnnotatorState aModel)
    {
        setDefaultModelObject(aModel);
    }
    
    public AnnotatorState getModelObject()
    {
        return (AnnotatorState) getDefaultModelObject();
    }
    
    private List<SourceDocument> getListOfDocs()
    {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.get(username);
        // List of all Source Documents in the project
        List<SourceDocument> listOfSourceDocuements = repository
                .listSourceDocuments(getModelObject().getProject());
        List<SourceDocument> sourceDocumentsInIgnoreState = new ArrayList<SourceDocument>();
        for (SourceDocument sourceDocument : listOfSourceDocuements) {
            if (repository.existsAnnotationDocument(sourceDocument, user)
                    && repository.getAnnotationDocument(sourceDocument, user).getState()
                            .equals(AnnotationDocumentState.IGNORE)) {
                sourceDocumentsInIgnoreState.add(sourceDocument);
            }
        }

        listOfSourceDocuements.removeAll(sourceDocumentsInIgnoreState);
        return listOfSourceDocuements;
    }

    private void updateSentenceAddress(JCas aJCas, AjaxRequestTarget aTarget)
        throws UIMAException, IOException, ClassNotFoundException
    {
        AnnotatorState state = AnnotationPage.this.getModelObject();
        
        gotoPageAddress = WebAnnoCasUtil.getSentenceAddress(aJCas,
                gotoPageTextField.getModelObject());

        String labelText = "";
        if (state.getDocument() != null) {
        	
        	List<SourceDocument> listofDoc = getListOfDocs();
        	
        	int docIndex = listofDoc.indexOf(state.getDocument())+1;
        	
            totalNumberOfSentence = WebAnnoCasUtil.getNumberOfPages(aJCas);

            // If only one page, start displaying from sentence 1
            if (totalNumberOfSentence == 1) {
                state.setFirstVisibleSentence(WebAnnoCasUtil.getFirstSentence(aJCas));
            }

            labelText = "showing " + state.getFirstVisibleSentenceNumber() + "-"
                    + state.getLastVisibleSentenceNumber() + " of " + totalNumberOfSentence
                    + " sentences [document " + docIndex + " of " + listofDoc.size() + "]";
        }
        else {
            labelText = "";// no document yet selected
        }

        numberOfPages.setDefaultModelObject(labelText);
        aTarget.add(numberOfPages);
        aTarget.add(gotoPageTextField);
    }

    @Override
    public void renderHead(IHeaderResponse response)
    {
        super.renderHead(response);

        String jQueryString = "";
        if (firstLoad) {
            jQueryString += "jQuery('#showOpenDocumentModal').trigger('click');";
            firstLoad = false;
        }
        response.render(OnLoadHeaderItem.forScript(jQueryString));
    }

    private JCas getJCas()
        throws UIMAException, IOException, ClassNotFoundException
    {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.get(username);

        SourceDocument aDocument = getModelObject().getDocument();

        AnnotationDocument annotationDocument = repository.getAnnotationDocument(aDocument, user);

        // If there is no CAS yet for the annotation document, create one.
        return repository.readAnnotationCas(annotationDocument);
    }

    private void updateSentenceNumber(JCas aJCas, int aAddress)
    {
        AnnotatorState state = AnnotationPage.this.getModelObject();
        
        Sentence sentence = selectByAddr(aJCas, Sentence.class, aAddress);
        state.setFirstVisibleSentence(sentence);
        state.setFocusSentenceNumber(
                WebAnnoCasUtil.getSentenceNumber(aJCas, sentence.getBegin()));
    }

    private void actionOpenDocument(AjaxRequestTarget aTarget)
    {
        getModelObject().getSelection().clear();
        openDocumentsModal.show(aTarget);
    }

    /**
     * Show the previous document, if exist
     */
    private void actionShowPreviousDocument(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        
        editor.reset(aTarget);
        // List of all Source Documents in the project
        List<SourceDocument> listOfSourceDocuements = getListOfDocs();

        // Index of the current source document in the list
        int currentDocumentIndex = listOfSourceDocuements.indexOf(state.getDocument());

        // If the first the document
        if (currentDocumentIndex == 0) {
            aTarget.appendJavaScript("alert('This is the first document!')");
            return;
        }
        state.setDocument(listOfSourceDocuements.get(currentDocumentIndex - 1));

        actionLoadDocument(aTarget);
    }

    /**
     * Show the next document if exist
     */
    private void actionShowNextDocument(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        
        editor.reset(aTarget);
        // List of all Source Documents in the project
        List<SourceDocument> listOfSourceDocuements = getListOfDocs();

        // Index of the current source document in the list
        int currentDocumentIndex = listOfSourceDocuements.indexOf(state.getDocument());

        // If the first document
        if (currentDocumentIndex == listOfSourceDocuements.size() - 1) {
            aTarget.appendJavaScript("alert('This is the last document!')");
            return;
        }
        state.setDocument(listOfSourceDocuements.get(currentDocumentIndex + 1));

        actionLoadDocument(aTarget);
    }

    private void actionGotoPage(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        
        try {
            if (gotoPageAddress == 0) {
                aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
                return;
            }
            if (state.getDocument() == null) {
                aTarget.appendJavaScript("alert('Please open a document first!')");
                return;
            }
            if (state.getFirstVisibleSentenceAddress() != gotoPageAddress) {
                JCas jCas = getJCas();
                updateSentenceNumber(jCas, gotoPageAddress);
                updateSentenceAddress(jCas, aTarget);
                annotator.bratRenderLater(aTarget);
            }
        }
        catch (Exception e) {
            error(e.getMessage());
            aTarget.addChildren(getPage(), FeedbackPanel.class);
        }
    }

    private void actionEnterPageNumer(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        
        try {
            if (gotoPageAddress == 0) {
                aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
                return;
            }
            if (state.getFirstVisibleSentenceAddress() != gotoPageAddress) {
                JCas jCas = getJCas();

                updateSentenceNumber(jCas, gotoPageAddress);

                annotator.bratRenderLater(aTarget);
                gotoPageTextField.setModelObject(state.getFirstVisibleSentenceNumber());

                aTarget.addChildren(getPage(), FeedbackPanel.class);
                aTarget.add(numberOfPages);
                aTarget.add(gotoPageTextField);
            }
        }
        catch (Exception e) {
            error(e.getMessage());
            aTarget.addChildren(getPage(), FeedbackPanel.class);
        }
    }

    /**
     * Show the previous page of this document
     */
    private void actionShowPreviousPage(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        
        try {
            if (state.getDocument() != null) {
    
                JCas jCas = getJCas();
                int firstSentenceAddress = WebAnnoCasUtil.getFirstSentenceAddress(jCas);
    
                int previousSentenceAddress = WebAnnoCasUtil
                        .getPreviousDisplayWindowSentenceBeginAddress(jCas,
                                state.getFirstVisibleSentenceAddress(),
                                state.getPreferences().getWindowSize());
                // Since BratAjaxCasUtil.getPreviousDisplayWindowSentenceBeginAddress returns same
                // address
                // if there are not much sentences to go back to as defined in windowSize
                if (previousSentenceAddress == state.getFirstVisibleSentenceAddress() &&
                // Check whether it's not the beginning of document
                        state.getFirstVisibleSentenceAddress() != firstSentenceAddress) {
                    previousSentenceAddress = firstSentenceAddress;
                }
    
                if (state.getFirstVisibleSentenceAddress() != previousSentenceAddress) {
                    updateSentenceNumber(jCas, previousSentenceAddress);
    
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    annotator.bratRenderLater(aTarget);
                    gotoPageTextField.setModelObject(state.getFirstVisibleSentenceNumber());
                    updateSentenceAddress(jCas, aTarget);
                }
                else {
                    aTarget.appendJavaScript("alert('This is First Page!')");
                }
            }
            else {
                aTarget.appendJavaScript("alert('Please open a document first!')");
            }
        }
        catch (Exception e) {
            error(e.getMessage());
            aTarget.addChildren(getPage(), FeedbackPanel.class);
        }
    }

    /**
     * Show the next page of this document
     */
    private void actionShowNextPage(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        
        try {
            if (state.getDocument() != null) {
                JCas jCas = getJCas();
                int nextSentenceAddress = WebAnnoCasUtil.getNextPageFirstSentenceAddress(jCas,
                        state.getFirstVisibleSentenceAddress(),
                        state.getPreferences().getWindowSize());
                if (state.getFirstVisibleSentenceAddress() != nextSentenceAddress) {
    
                    updateSentenceNumber(jCas, nextSentenceAddress);
    
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    annotator.bratRenderLater(aTarget);
                    gotoPageTextField.setModelObject(state.getFirstVisibleSentenceNumber());
                    updateSentenceAddress(jCas, aTarget);
                }
    
                else {
                    aTarget.appendJavaScript("alert('This is last page!')");
                }
            }
            else {
                aTarget.appendJavaScript("alert('Please open a document first!')");
            }
        }
        catch (Exception e) {
            error(e.getMessage());
            aTarget.addChildren(getPage(), FeedbackPanel.class);
        }
    }

    private void actionShowFirstPage(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        
        try {
            if (state.getDocument() != null) {
    
                JCas jCas = getJCas();
                int firstSentenceAddress = WebAnnoCasUtil.getFirstSentenceAddress(jCas);
    
                if (firstSentenceAddress != state.getFirstVisibleSentenceAddress()) {
                    updateSentenceNumber(jCas, firstSentenceAddress);
    
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    annotator.bratRenderLater(aTarget);
                    gotoPageTextField.setModelObject(state.getFirstVisibleSentenceNumber());
                    updateSentenceAddress(jCas, aTarget);
                }
                else {
                    aTarget.appendJavaScript("alert('This is first page!')");
                }
            }
            else {
                aTarget.appendJavaScript("alert('Please open a document first!')");
            }
        }
        catch (Exception e) {
            error(e.getMessage());
            aTarget.addChildren(getPage(), FeedbackPanel.class);
        }
    }

    private void actionShowLastPage(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        
        try {
            if (state.getDocument() != null) {

                JCas jCas = getJCas();

                int lastDisplayWindowBeginingSentenceAddress = WebAnnoCasUtil
                        .getLastDisplayWindowFirstSentenceAddress(jCas,
                                state.getPreferences().getWindowSize());
                if (lastDisplayWindowBeginingSentenceAddress != state
                        .getFirstVisibleSentenceAddress()) {

                    updateSentenceNumber(jCas, lastDisplayWindowBeginingSentenceAddress);

                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    annotator.bratRenderLater(aTarget);
                    gotoPageTextField.setModelObject(state.getFirstVisibleSentenceNumber());
                    updateSentenceAddress(jCas, aTarget);
                }
                else {
                    aTarget.appendJavaScript("alert('This is last Page!')");
                }
            }
            else {
                aTarget.appendJavaScript("alert('Please open a document first!')");
            }
        }
        catch (Exception e) {
            error(e.getMessage());
            aTarget.addChildren(getPage(), FeedbackPanel.class);
        }
    }

    private void actionToggleScriptDirection(AjaxRequestTarget aTarget)
    {
        getModelObject().toggleScriptDirection();
        annotator.bratRenderLater(aTarget);
    }

    private void actionLoadDocument(AjaxRequestTarget aTarget)
    {
        LOG.info("BEGIN LOAD_DOCUMENT_ACTION");

        AnnotatorState state = getModelObject();
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.get(username);

        state.setUser(userRepository.get(username));

        try {
            // Check if there is an annotation document entry in the database. If there is none,
            // create one.
            AnnotationDocument annotationDocument = repository
                    .createOrGetAnnotationDocument(state.getDocument(), user);

            // Read the CAS
            JCas annotationCas = repository.readAnnotationCas(annotationDocument);

            // Update the annotation document CAS
            repository.upgradeCas(annotationCas.getCas(), annotationDocument);

            // After creating an new CAS or upgrading the CAS, we need to save it
            repository.writeAnnotationCas(annotationCas.getCas().getJCas(),
                    annotationDocument.getDocument(), user);

            // (Re)initialize brat model after potential creating / upgrading CAS
            state.initForDocument(annotationCas, repository);

            // Load constraints
            state.setConstraints(repository.loadConstraints(state.getProject()));

            // Load user preferences
            PreferencesUtil.setAnnotationPreference(username, repository, annotationService, state,
                    state.getMode());

            // if project is changed, reset some project specific settings
            if (currentprojectId != state.getProject().getId()) {
                state.clearRememberedFeatures();
            }

            currentprojectId = state.getProject().getId();

            LOG.debug("Configured BratAnnotatorModel for user [" + state.getUser() + "] f:["
                    + state.getFirstVisibleSentenceNumber() + "] l:["
                    + state.getLastVisibleSentenceNumber() + "] s:["
                    + state.getFocusSentenceNumber() + "]");

            gotoPageTextField.setModelObject(1);

            updateSentenceAddress(annotationCas, aTarget);

            // Re-render the whole page because the font size
            aTarget.add(AnnotationPage.this);

            // Update document state
            if (state.getDocument().getState().equals(SourceDocumentState.NEW)) {
                state.getDocument().setState(SourceDocumentStateTransition
                        .transition(SourceDocumentStateTransition.NEW_TO_ANNOTATION_IN_PROGRESS));
                repository.createSourceDocument(state.getDocument());
            }
        }
        catch (UIMAException e) {
            LOG.error("Error", e);
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            error(ExceptionUtils.getRootCauseMessage(e));
        }
        catch (Exception e) {
            LOG.error("Error", e);
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            error("Error: " + e.getMessage());
        }

        LOG.info("END LOAD_DOCUMENT_ACTION");
    }
}
