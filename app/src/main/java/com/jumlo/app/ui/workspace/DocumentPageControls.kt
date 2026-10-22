package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import com.jumlo.app.ui.icons.*
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jumlo.app.R
import com.jumlo.app.ui.theme.QuietTheme

/** Preview navigation chooses the actual export page for a placement tool. */
@Composable
internal fun DocumentPageControls(page:Int,pageCount:Int,onPage:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,
        verticalAlignment=Alignment.CenterVertically) {
        IconButton(enabled=page>1,onClick={onPage(page-1)}) {
            Icon(Icons.AutoMirrored.Rounded.NavigateBefore,stringResource(R.string.action_previous))
        }
        Text(stringResource(R.string.ui_page_indicator,page,pageCount),style=QuietTheme.typography.metadataStrong)
        IconButton(enabled=page<pageCount,onClick={onPage(page+1)}) {
            Icon(Icons.AutoMirrored.Rounded.NavigateNext,stringResource(R.string.action_next))
        }
    }
}
