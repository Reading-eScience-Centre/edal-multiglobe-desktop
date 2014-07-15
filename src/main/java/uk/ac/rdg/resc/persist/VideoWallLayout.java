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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import uk.ac.rdg.resc.MultiGlobeFrame;
import uk.ac.rdg.resc.RescModel;

@XmlType(namespace = VideoWallLayout.NAMESPACE, name = "VideoWallLayoutType")
@XmlRootElement(namespace = VideoWallLayout.NAMESPACE, name = "videowallLayout")
public class VideoWallLayout {
    public static final String NAMESPACE = "http://www.resc.reading.ac.uk/videowall";

    @XmlElement(name = "row")
    private List<VideoWallRow> rows;

    @SuppressWarnings("unused")
    private VideoWallLayout() {
    };

    public VideoWallLayout(List<VideoWallRow> rows) {
        this.rows = rows;
    }
    
    public List<VideoWallRow> getRows() {
        return rows;
    }

    public static VideoWallLayout fromMultiGlobeFrame(MultiGlobeFrame frame) {
        int columns = frame.getColumns();
        int rows = frame.getRows();

        List<VideoWallRow> rowList = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            List<VideoWallContents> columnList = new ArrayList<>();
            for (int j = 0; j < columns; j++) {
                RescModel model = frame.getModelAt(i, j);
                VideoWallContents contents = VideoWallContents.fromRescModel(model);
                columnList.add(contents);
            }
            VideoWallRow row = new VideoWallRow(columnList);
            rowList.add(row);
        }
        VideoWallLayout layout = new VideoWallLayout(rowList);
        return layout;
    }
    
    public static void toFile(VideoWallLayout layout, File file) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(VideoWallLayout.class);

        Marshaller marshaller = context.createMarshaller();

        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        marshaller.marshal(layout, file);
    }
    
    public static VideoWallLayout fromFile(File file) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(VideoWallLayout.class);

        Unmarshaller unmarshaller = context.createUnmarshaller();

        VideoWallLayout layout = (VideoWallLayout) unmarshaller.unmarshal(file);

        return layout;
    }
}
