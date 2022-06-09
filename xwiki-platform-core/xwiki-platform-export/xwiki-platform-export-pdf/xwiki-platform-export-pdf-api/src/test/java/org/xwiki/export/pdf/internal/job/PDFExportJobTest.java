/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.export.pdf.internal.job;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.export.pdf.PDFPrinter;
import org.xwiki.export.pdf.internal.RequiredSkinExtensionsRecorder;
import org.xwiki.export.pdf.job.PDFExportJobRequest;
import org.xwiki.export.pdf.job.PDFExportJobStatus;
import org.xwiki.export.pdf.job.PDFExportJobStatus.DocumentRenderingResult;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.resource.temporary.TemporaryResourceReference;
import org.xwiki.resource.temporary.TemporaryResourceStore;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PDFExportJob}.
 * 
 * @version $Id$
 */
@ComponentTest
class PDFExportJobTest
{
    @InjectMockComponents
    private PDFExportJob pdfExportJob;

    @MockComponent
    private AuthorizationManager authorization;

    @MockComponent
    private DocumentRenderer documentRenderer;

    @MockComponent
    private RequiredSkinExtensionsRecorder requiredSkinExtensionsRecorder;

    @MockComponent
    @Named("docker")
    private PDFPrinter<PDFExportJobRequest> pdfPrinter;

    @MockComponent
    private TemporaryResourceStore temporaryResourceStore;

    private DocumentReference firstPageReference = new DocumentReference("test", "First", "Page");

    private DocumentRenderingResult firstPageRendering = new DocumentRenderingResult(this.firstPageReference,
        new XDOM(Collections.singletonList(new WordBlock("first"))), "first HTML");

    private DocumentReference secondPageReference = new DocumentReference("test", "Second", "Page");

    private DocumentRenderingResult secondPageRendering = new DocumentRenderingResult(this.secondPageReference,
        new XDOM(Collections.singletonList(new WordBlock("second"))), "second HTML");

    private PDFExportJobRequest request = new PDFExportJobRequest();

    private DocumentReference aliceReference = new DocumentReference("test", "Users", "Alice");

    private DocumentReference bobReference = new DocumentReference("test", "Users", "Bob");

    @BeforeEach
    void configure() throws Exception
    {
        DocumentReference thirdPageReference = new DocumentReference("test", "Third", "Page");
        when(this.authorization.hasAccess(Right.VIEW, this.aliceReference, thirdPageReference)).thenReturn(true);
        DocumentReference fourthPageReference = new DocumentReference("test", "Fourth", "Page");
        when(this.authorization.hasAccess(Right.VIEW, this.bobReference, fourthPageReference)).thenReturn(true);

        this.request.setCheckRights(true);
        this.request.setCheckAuthorRights(true);
        this.request.setUserReference(this.aliceReference);
        this.request.setAuthorReference(this.bobReference);
        this.request.setDocuments(
            Arrays.asList(this.firstPageReference, this.secondPageReference, thirdPageReference, fourthPageReference));

        when(this.authorization.hasAccess(Right.VIEW, this.aliceReference, this.firstPageReference)).thenReturn(true);
        when(this.authorization.hasAccess(Right.VIEW, this.aliceReference, this.secondPageReference)).thenReturn(true);

        when(this.authorization.hasAccess(Right.VIEW, this.bobReference, this.firstPageReference)).thenReturn(true);
        when(this.authorization.hasAccess(Right.VIEW, this.bobReference, this.secondPageReference)).thenReturn(true);

        when(this.documentRenderer.render(this.firstPageReference)).thenReturn(this.firstPageRendering);
        when(this.documentRenderer.render(this.secondPageReference)).thenReturn(this.secondPageRendering);
    }

    @Test
    void run() throws Exception
    {
        when(this.requiredSkinExtensionsRecorder.stop()).thenReturn("required skin extensions");

        InputStream pdfContent = mock(InputStream.class);
        when(this.pdfPrinter.print(this.request)).thenReturn(pdfContent);

        this.pdfExportJob.initialize(this.request);
        this.pdfExportJob.runInternal();

        verify(this.requiredSkinExtensionsRecorder).start();

        PDFExportJobStatus jobStatus = this.pdfExportJob.getStatus();
        assertEquals("required skin extensions", jobStatus.getRequiredSkinExtensions());
        assertEquals(0, jobStatus.getDocumentRenderingResults().size());

        TemporaryResourceReference pdfFileReference = jobStatus.getPDFFileReference();
        verify(this.temporaryResourceStore).createTemporaryFile(pdfFileReference, pdfContent);
    }

    @Test
    void runClientSide() throws Exception
    {
        this.request.setServerSide(false);
        this.pdfExportJob.initialize(this.request);
        this.pdfExportJob.runInternal();

        PDFExportJobStatus jobStatus = this.pdfExportJob.getStatus();
        assertNull(jobStatus.getPDFFileReference());

        TemporaryResourceReference pdfFileReference = jobStatus.getPDFFileReference();
        verify(this.temporaryResourceStore, never()).createTemporaryFile(eq(pdfFileReference), any(InputStream.class));

        List<DocumentRenderingResult> renderingResults = jobStatus.getDocumentRenderingResults();
        assertEquals(2, renderingResults.size());
        assertSame(this.firstPageRendering, renderingResults.get(0));
        assertSame(this.secondPageRendering, renderingResults.get(1));
    }

    @Test
    void runWithoutDocuments() throws Exception
    {
        this.request.setDocuments(Collections.emptyList());
        this.pdfExportJob.initialize(this.request);
        this.pdfExportJob.runInternal();

        PDFExportJobStatus jobStatus = this.pdfExportJob.getStatus();
        assertNull(jobStatus.getPDFFileReference());
        assertNull(jobStatus.getRequiredSkinExtensions());
        assertEquals(0, jobStatus.getDocumentRenderingResults().size());
    }
}
