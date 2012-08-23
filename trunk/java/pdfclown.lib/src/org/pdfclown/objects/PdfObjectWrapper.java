/*
  Copyright 2006-2012 Stefano Chizzolini. http://www.pdfclown.org

  Contributors:
    * Stefano Chizzolini (original code developer, http://www.stefanochizzolini.it)

  This file should be part of the source code distribution of "PDF Clown library"
  (the Program): see the accompanying README files for more info.

  This Program is free software; you can redistribute it and/or modify it under the terms
  of the GNU Lesser General Public License as published by the Free Software Foundation;
  either version 3 of the License, or (at your option) any later version.

  This Program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY,
  either expressed or implied; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE. See the License for more details.

  You should have received a copy of the GNU Lesser General Public License along with this
  Program (see README files); if not, go to the GNU website (http://www.gnu.org/licenses/).

  Redistribution and use, with or without modification, are permitted provided that such
  redistributions retain the above copyright notice, license and disclaimer, along with
  this list of conditions.
*/

package org.pdfclown.objects;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Collection;

import org.pdfclown.PDF;
import org.pdfclown.Version;
import org.pdfclown.VersionEnum;
import org.pdfclown.documents.Document;
import org.pdfclown.documents.Document.Configuration.CompatibilityModeEnum;
import org.pdfclown.documents.interchange.metadata.Metadata;
import org.pdfclown.files.File;
import org.pdfclown.util.NotImplementedException;

/**
  High-level representation of a PDF object.
  <p>Specialized objects don't inherit directly from their low-level counterparts
  (e.g. {@link org.pdfclown.documents.contents.Contents Contents} extends
  {@link org.pdfclown.objects.PdfStream PdfStream}, {@link org.pdfclown.documents.Pages Pages}
  extends {@link org.pdfclown.objects.PdfArray PdfArray} and so on) because there's no plain one-to
  one mapping between primitive PDF types and specialized instances: the <code>Content</code> entry
  of <code>Page</code> dictionaries may be a simple reference to a <code>PdfStream</code> or a
  <code>PdfArray</code> of references to <code>PdfStream</code>-s, <code>Pages</code> collections
  may be spread across a B-tree instead of a flat <code>PdfArray</code> and so on.</p>
  <p>So, <i>in order to hide all these annoying inner workings, I chose to adopt a composition
  pattern instead of the apparently-reasonable (but actually awkward!) inheritance pattern</i>.
  Nonetheless, users can navigate through the low-level structure getting the
  {@link #getBaseDataObject() baseDataObject} backing this object.</p>

  @author Stefano Chizzolini (http://www.stefanochizzolini.it)
  @version 0.1.2, 08/23/12
*/
public abstract class PdfObjectWrapper<TDataObject extends PdfDataObject>
  implements IPdfObjectWrapper
{
  // <class>
  // <static>
  // <interface>
  // <public>
  /**
    Gets the PDF object backing the specified wrapper.

    @param wrapper Object to extract the base from.
  */
  public static PdfDirectObject getBaseObject(
    PdfObjectWrapper<?> wrapper
    )
  {return (wrapper != null ? wrapper.getBaseObject() : null);}
  // </public>
  // </interface>
  // </static>

  // <dynamic>
  // <fields>
  private PdfDirectObject baseObject;
  // </fields>

  // <constructors>
  /**
    Creates a new wrapper into the specified document context.

    @param context Document context into which the specified data object has to be registered.
    @param baseDataObject PDF data object backing this wrapper.
  */
  protected PdfObjectWrapper(
    Document context,
    TDataObject baseDataObject
    )
  {this(context.getFile(), baseDataObject);}

  /**
    Creates a new wrapper into the specified file context.

    @param context File context into which the specified data object has to be registered.
    @param baseDataObject PDF data object backing this wrapper.
  */
  protected PdfObjectWrapper(
    File context,
    TDataObject baseDataObject
    )
  {this(context.register(baseDataObject));}

  /**
    Instantiates a wrapper in case of reference or mutable data object.

    @param baseObject PDF object backing this wrapper. MUST be a {@link PdfReference reference}
    everytime available.
  */
  protected PdfObjectWrapper(
    PdfDirectObject baseObject
    )
  {setBaseObject(baseObject);}
  // </constructors>

  // <interface>
  // <public>
  /**
    Gets a clone of the object, registered inside the given document context.

    @param context Which document the clone has to be registered in.
  */
  public abstract Object clone(
    Document context
    );

  /**
    Removes the object from its document context.
    <h3>Remarks</h3>
    <p>The object is no more usable after this method returns.</p>

    @return Whether the object was actually decontextualized (only indirect objects can be
    decontextualized).
  */
  public boolean delete(
    )
  {
    // Is the object indirect?
    if(baseObject instanceof PdfReference) // Indirect object.
    {
      ((PdfReference)baseObject).delete();
      return true;
    }
    else // Direct object.
    {return false;}
  }

  /**
    Gets the underlying data object.
  */
  @SuppressWarnings("unchecked")
  public TDataObject getBaseDataObject(
    )
  {return (TDataObject)File.resolve(baseObject);}

  /**
    Gets the indirect object containing the base object.
  */
  public PdfIndirectObject getContainer(
    )
  {return baseObject.getContainer();}

  /**
    Gets the document context.
  */
  public Document getDocument(
    )
  {
    File file = getFile();
    return file != null ? file.getDocument() : null;
  }

  /**
    Gets the file context.
  */
  public File getFile(
    )
  {
    PdfIndirectObject container = getContainer();
    return container != null ? container.getFile() : null;
  }

  /**
    Gets the metadata associated to this object.
  */
  public Metadata getMetadata(
    )
  {
    PdfDictionary dictionary = getDictionary();
    if(dictionary == null)
      return null;

    PdfDirectObject metadataObject = dictionary.get(PdfName.Metadata);
    return metadataObject != null ? new Metadata(metadataObject) : null;
  }

  /**
    @see #getMetadata()
  */
  public void setMetadata(
    Metadata value
    )
  {
    PdfDictionary dictionary = getDictionary();
    if(dictionary == null)
      throw new UnsupportedOperationException("Metadata can be attached only to PdfDictionary/PdfStream base data objects.");

    dictionary.put(PdfName.Metadata, value.getBaseObject());
  }

  // <IPdfObjectWrapper>
  @Override
  public PdfDirectObject getBaseObject(
    )
  {return baseObject;}
  // </IPdfObjectWrapper>
  // </public>

  // <protected>
  /**
    Checks whether the specified feature is compatible with the {@link Document#getVersion() document's conformance version}.

    @param feature Entity whose compatibility has to be checked. Supported types:
      <ul>
        <li>{@link VersionEnum}</li>
        <li>{@link String Property name} resolvable to an {@link AnnotatedElement annotated getter method}</li>
        <li>{@link AnnotatedElement}</li>
      </ul>
    @throws RuntimeException In case of version conflict (see {@link org.pdfclown.documents.Document.Configuration.CompatibilityModeEnum#Strict Strict compatibility mode}).
    @since 0.1.0
  */
  protected void checkCompatibility(
    Object feature
    )
  {
    /*
      TODO: Caching!
    */
    CompatibilityModeEnum compatibilityMode = getDocument().getConfiguration().getCompatibilityMode();
    if(compatibilityMode == CompatibilityModeEnum.Passthrough) // No check required.
      return;

    if(feature instanceof Collection<?>)
    {
      for(Object featureItem : (Collection<?>)feature)
      {checkCompatibility(featureItem);}
      return;
    }

    Version featureVersion;
    if(feature instanceof VersionEnum) // Explicit version.
    {featureVersion = ((VersionEnum)feature).getVersion();}
    else // Implicit version (element annotation).
    {
      PDF annotation;
      {
        if(feature instanceof String) // Property name.
        {
          BeanInfo classInfo;
          try
          {classInfo = Introspector.getBeanInfo(getClass());}
          catch(IntrospectionException e)
          {throw new RuntimeException(e);}
          for(PropertyDescriptor property : classInfo.getPropertyDescriptors())
          {
            if(feature.equals(property.getName()))
            {
              feature = property.getReadMethod();
              break;
            }
          }
        }
        else if(feature instanceof Enum<?>) // Enum constant.
        {
          try
          {feature = feature.getClass().getField(((Enum<?>)feature).name());}
          catch(NoSuchFieldException e)
          {throw new RuntimeException(e);}
        }
        if(!(feature instanceof AnnotatedElement))
          throw new IllegalArgumentException("Feature type '" + feature.getClass().getName() + "' not supported.");

        while(true)
        {
           annotation = ((AnnotatedElement)feature).getAnnotation(PDF.class);
           if(annotation != null)
             break;

           if(feature instanceof Member)
           {feature = ((Member)feature).getDeclaringClass();}
           else if(feature instanceof Class<?>)
           {
             Class<?> containerClass = ((Class<?>)feature).getDeclaringClass();
             feature = (containerClass != null ? containerClass : ((Class<?>)feature).getPackage());
           }
           else // Element hierarchy walk complete.
             return; // NOTE: As no annotation is available, we assume the feature has no specific compatibility requirements.
        }
      }
      featureVersion = annotation.value().getVersion();
    }
    // Is the feature version compatible?
    if(getDocument().getVersion().compareTo(featureVersion) >= 0)
      return;

    // The feature version is NOT compatible: how to solve the conflict?
    switch(compatibilityMode)
    {
      case Loose: // Accepts the feature version.
        // Synchronize the document version!
        getDocument().setVersion(featureVersion);
        break;
      case Strict: // Refuses the feature version.
        // Throw a violation to the document version!
        throw new RuntimeException("Incompatible feature (version " + featureVersion + " was required against document version " + getDocument().getVersion());
      default:
        throw new NotImplementedException("Unhandled compatibility mode: " + compatibilityMode.name());
    }
  }

  protected void setBaseObject(
    PdfDirectObject value
    )
  {baseObject = value;}
  // </protected>

  // <private>
  private PdfDictionary getDictionary(
    )
  {
    if(getBaseDataObject() instanceof PdfDictionary)
      return (PdfDictionary)getBaseDataObject();
    else if(getBaseDataObject() instanceof PdfStream)
      return ((PdfStream)getBaseDataObject()).getHeader();
    else
      return null;
  }
  // </private>
  // </interface>
  // </dynamic>
  // </class>
}