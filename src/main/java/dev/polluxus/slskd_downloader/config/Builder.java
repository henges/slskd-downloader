package dev.polluxus.slskd_downloader.config;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.lang.annotation.*;

@RecordBuilder.Template(options = @RecordBuilder.Options(
        prefixEnclosingClassNames = false
))
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Inherited
public @interface Builder {
}
