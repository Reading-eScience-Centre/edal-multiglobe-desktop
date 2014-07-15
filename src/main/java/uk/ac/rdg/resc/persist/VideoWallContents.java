/*******************************************************************************
 * Copyright (c) 2014 The University of Reading
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package uk.ac.rdg.resc.persist;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.joda.time.DateTime;
import org.joda.time.chrono.ISOChronology;

import uk.ac.rdg.resc.EdalDataLayer;
import uk.ac.rdg.resc.LinkedView.LinkedViewState;
import uk.ac.rdg.resc.RescModel;
import uk.ac.rdg.resc.RescModel.Projection;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsVariable;
import uk.ac.rdg.resc.edal.util.TimeUtils;

@XmlType(namespace = VideoWallLayout.NAMESPACE, name = "VideoWallPanelType")
public class VideoWallContents {
    @XmlElement
    private boolean flatMap = false;
    @XmlElement
    private Projection mapProjection = null;
    @XmlElement
    private Double elevation = null;
    @XmlElement
    @XmlJavaTypeAdapter(DateTimeAdapter.class)
    private DateTime time = null;
    @XmlElement
    private Double opacity = 1.0;
    @XmlElement
    private LinkedViewState linkedViewState = LinkedViewState.LINKED;
    /*
     * By using this, we get all of the XML stuff we need automatically! We also
     * get a variable ID which we can use to store the layer name! We can ignore
     * the title/description, but when generating it we should generate it from
     * the catalogue.
     */
    @XmlElement
    private NcwmsVariable plottingMetadata;

    public VideoWallContents() {
        /*
         * TODO Make this private once tested properly
         */
    }

    public boolean isFlatMap() {
        return flatMap;
    }

    public Projection getMapProjection() {
        return mapProjection;
    }

    public Double getElevation() {
        return elevation;
    }

    public DateTime getTime() {
        return time;
    }

    public Double getOpacity() {
        return opacity;
    }

    public LinkedViewState getLinkedViewState() {
        return linkedViewState;
    }

    public NcwmsVariable getPlottingMetadata() {
        return plottingMetadata;
    }

    public static VideoWallContents fromRescModel(RescModel model) {
        VideoWallContents contents = new VideoWallContents();
        contents.flatMap = model.isFlat();
        if (contents.flatMap) {
            contents.mapProjection = model.getProjection();
        }
        contents.linkedViewState = model.getWorldWindow().getView().getLinkedViewState();
        EdalDataLayer dataLayer = model.getDataLayer();
        if (dataLayer != null) {
            contents.elevation = dataLayer.getElevation();
            contents.time = dataLayer.getTime();
            contents.opacity = dataLayer.getOpacity();
            contents.plottingMetadata = dataLayer.getPlottingMetadata();
        }

        return contents;
    }

    private static class DateTimeAdapter extends XmlAdapter<String, DateTime> {
        private DateTimeAdapter() {
        }

        @Override
        public DateTime unmarshal(String timeStr) throws Exception {
            return TimeUtils.iso8601ToDateTime(timeStr, ISOChronology.getInstance());
        }

        @Override
        public String marshal(DateTime time) throws Exception {
            return TimeUtils.dateTimeToISO8601(time);
        }

        private static DateTimeAdapter adapter = new DateTimeAdapter();

        @SuppressWarnings("unused")
        public static DateTimeAdapter getInstance() {
            return adapter;
        }
    }
}
