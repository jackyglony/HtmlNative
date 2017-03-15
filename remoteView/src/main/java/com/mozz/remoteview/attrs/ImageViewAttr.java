package com.mozz.remoteview.attrs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ImageView;

import com.mozz.remoteview.AttrApplyException;
import com.mozz.remoteview.RVDomElement;
import com.mozz.remoteview.RVRenderer;

public class ImageViewAttr implements Attr {

    @Override
    public void apply(final Context context, String tag, final View v, @NonNull String params,
                      @NonNull final Object value, RVDomElement tree) throws AttrApplyException {
        if (params.equals("src") && RVRenderer.getImageViewAdpater() != null) {
            RVRenderer.getImageViewAdpater().setImage(value.toString(), (ImageView) v);
        }
    }
}
