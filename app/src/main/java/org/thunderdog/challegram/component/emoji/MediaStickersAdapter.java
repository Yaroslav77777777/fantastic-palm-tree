/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/02/2016 at 13:24
 */
package org.thunderdog.challegram.component.emoji;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.NonMaterialButton;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.SeparatorView;

import java.util.ArrayList;
import java.util.Random;

import me.vkryl.android.widget.FrameLayoutFix;

public class MediaStickersAdapter extends RecyclerView.Adapter<MediaStickersAdapter.StickerHolder> implements View.OnClickListener {

  private final ViewController<?> context;
  private final ArrayList<StickerItem> items;
  private final StickerSmallView.StickerMovementCallback callback;
  private final boolean isTrending;
  private @Nullable RecyclerView.LayoutManager manager;
  private @Nullable ViewController<?> themeProvider;

  private boolean isBig;

  public void setIsBig () {
    isBig = true;
  }

  public MediaStickersAdapter (ViewController<?> context, StickerSmallView.StickerMovementCallback callback, boolean isTrending, @Nullable ViewController<?> themeProvider) {
    this.context = context;
    this.callback = callback;
    this.isTrending = isTrending;
    this.themeProvider = themeProvider;
    this.items = new ArrayList<>();
  }

  public void setManager (@NonNull RecyclerView.LayoutManager manager) {
    this.manager = manager;
  }

  @Override
  public StickerHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    return StickerHolder.create(context.context(), context.tdlib(), viewType, isTrending, this, callback, isBig, themeProvider);
  }

  public int measureScrollTop (int position, int spanCount, int sectionIndex, ArrayList<TGStickerSetInfo> sections, boolean haveRecentsTitle) {
    if (position == 0 || sections == null || sectionIndex == -1) {
      return 0;
    }

    position--;

    int scrollY = EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding();
    if (position == 0) {
      return scrollY;
    }


    final int rowSize = ((sections.get(0).isTrending() ? Screen.smallestSide() : Screen.currentWidth()) / spanCount);

    boolean hadFavorite = false;

    for (int i = 0; i < sectionIndex + 1 && position > 0 && i < sections.size(); i++) {
      TGStickerSetInfo stickerSet = sections.get(i);
      if (!stickerSet.isSystem() || stickerSet.isDefaultEmoji()) {
        scrollY += Screen.dp(stickerSet.isTrending() ? 52f : 32f);
        position--;
      } else if (stickerSet.isFavorite()) {
        // position--;
        hadFavorite = true;
      } else if (stickerSet.isRecent()) {
        position--;
        if (haveRecentsTitle) {
          scrollY += Screen.dp(32f);
        }
      }
      if (position > 0) {
        int itemCount = Math.min(stickerSet.isDefaultEmoji() ? stickerSet.getSize() + 1: stickerSet.isTrending() ? 5 : stickerSet.getSize(), position);
        int rowCount = (int) Math.ceil((double) itemCount / (double) spanCount);
        scrollY += rowCount * rowSize;
        position -= itemCount;
      }
    }

    return scrollY;
  }

  public void setStickerPressed (TGStickerObj sticker, boolean isPressed, @Nullable RecyclerView.LayoutManager manager) {
    int i = indexOfSticker(sticker, 0);
    if (i != -1) {
      setStickerPressed(i, isPressed, manager);
    }
  }

  public void setStickerPressed (int index, boolean isPressed, @Nullable RecyclerView.LayoutManager manager) {
    View view = manager != null ? manager.findViewByPosition(index) : null;
    if (view != null && view instanceof StickerSmallView) {
      ((StickerSmallView) view).setStickerPressed(isPressed);
    } else {
      notifyItemChanged(index);
    }
  }

  private LongSparseArray<TGStickerSetInfo> installingStickerSets;

  private boolean isInProgress (long setId) {
    return installingStickerSets != null && installingStickerSets.get(setId) != null;
  }

  private void updateInProgress (TGStickerSetInfo stickerSet) {
    if (manager == null) {
      return;
    }
    int i = stickerSet.getStartIndex();
    View view = manager.findViewByPosition(i);
    if (view != null && getItemViewType(i) == StickerHolder.TYPE_HEADER_TRENDING) {
      ((NonMaterialButton) ((ViewGroup) view).getChildAt(1)).setInProgress(isInProgress(stickerSet.getId()), true);
    } else {
      notifyItemChanged(i);
    }
  }

  public void updateState (TGStickerSetInfo stickerSet) {
    if (manager == null) {
      return;
    }
    int i = stickerSet.getStartIndex();
    View view = manager.findViewByPosition(i);
    if (view != null && getItemViewType(i) == StickerHolder.TYPE_HEADER_TRENDING) {
      ((NonMaterialButton) ((ViewGroup) view).getChildAt(1)).setIsDone(stickerSet.isInstalled(), false);
      ((ViewGroup) view).getChildAt(0).setVisibility(stickerSet.isViewed() ? View.GONE : View.VISIBLE);
    } else {
      notifyItemChanged(i);
    }
  }

  public void updateDone (TGStickerSetInfo stickerSet) {
    if (manager == null) {
      return;
    }
    int i = stickerSet.getStartIndex();
    View view = manager.findViewByPosition(i);
    if (view != null && getItemViewType(i) == StickerHolder.TYPE_HEADER_TRENDING && manager.getItemViewType(view) == StickerHolder.TYPE_HEADER_TRENDING) {
      ((NonMaterialButton) ((ViewGroup) view).getChildAt(1)).setIsDone(stickerSet.isInstalled(), true);
    } else {
      notifyItemChanged(i);
    }
  }

  private void installStickerSet (final TGStickerSetInfo stickerSet) {
    if (installingStickerSets == null) {
      installingStickerSets = new LongSparseArray<>();
    } else if (installingStickerSets.get(stickerSet.getId()) != null) {
      return;
    }
    installingStickerSets.put(stickerSet.getId(), stickerSet);
    context.tdlib().client().send(new TdApi.ChangeStickerSet(stickerSet.getId(), true, false), object -> context.tdlib().ui().post(() -> {
      installingStickerSets.remove(stickerSet.getId());
      updateInProgress(stickerSet);
      if (object.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
        stickerSet.setIsInstalled();
        updateDone(stickerSet);
      }
    }));
  }

  protected void onToggleCollapseRecentStickers (TextView collapseView, TGStickerSetInfo recentSet) {
    // override in children
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (tag != null && tag instanceof TGStickerSetInfo) {
      TGStickerSetInfo stickerSet = (TGStickerSetInfo) tag;
      final int viewId = v.getId();
      if (viewId == R.id.btn_addStickerSet) {
        ((NonMaterialButton) v).setInProgress(true, true);
        installStickerSet(stickerSet);
      } else if (viewId == R.id.btn_toggleCollapseRecentStickers) {
        onToggleCollapseRecentStickers((TextView) v, stickerSet);
        updateCollapseView((TextView) v, stickerSet);
      } else {
        stickerSet.show(context);
      }
    }
  }

  public void updateCollapseView (ViewGroup viewGroup, TGStickerSetInfo stickerSet) {
    View collapseView = viewGroup.findViewById(R.id.btn_toggleCollapseRecentStickers);
    if (collapseView instanceof TextView) {
      updateCollapseView((TextView) collapseView, stickerSet);
    }
  }

  public void updateCollapseView (TextView collapseView, TGStickerSetInfo stickerSet) {
    if (stickerSet != null && stickerSet.getFullSize() > Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT) {
      if (stickerSet.isCollapsed()) {
        int moreSize = stickerSet.getFullSize() - stickerSet.getSize();
        collapseView.setText(Lang.pluralBold(R.string.ShowXMoreStickers, moreSize));
      } else {
        collapseView.setText(R.string.ShowLessStickers);
      }
      collapseView.setVisibility(View.VISIBLE);
    } else {
      collapseView.setVisibility(View.GONE);
    }
  }

  @Override
  public void onBindViewHolder (StickerHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case StickerHolder.TYPE_EMOJI_STATUS_DEFAULT: {
        ((StickerSmallView) holder.itemView).setSticker(TGStickerObj.makeDefaultPremiumStar(context.tdlib()));
        break;
      }
      case StickerHolder.TYPE_STICKER: {
        TGStickerObj sticker = getSticker(position);
        if (sticker != null && sticker.isEmpty()) {
          sticker.requestRequiredInformation();
        }
        ((StickerSmallView) holder.itemView).setSticker(sticker);
        break;
      }
      case StickerHolder.TYPE_HEADER: {
        TGStickerSetInfo stickerSet = getStickerSet(position);
        Views.setMediumText(((TextView) holder.itemView), stickerSet != null ? stickerSet.getTitle() : "");
        Views.setTextGravity((TextView) holder.itemView, Lang.gravity());
        break;
      }
      case StickerHolder.TYPE_HEADER_COLLAPSABLE: {
        TGStickerSetInfo stickerSet = getStickerSet(position);
        TextView titleView = (TextView) ((ViewGroup) holder.itemView).getChildAt(0);
        Views.setMediumText(titleView, stickerSet != null ? stickerSet.getTitle() : "");
        Views.setTextGravity(titleView, Lang.gravity());

        TextView collapseView = (TextView) ((ViewGroup) holder.itemView).getChildAt(1);
        updateCollapseView(collapseView, stickerSet);
        collapseView.setTag(stickerSet);
        break;
      }
      case StickerHolder.TYPE_HEADER_TRENDING: {
        TGStickerSetInfo stickerSet = getStickerSet(position);
        if (stickerSet != null && !stickerSet.isViewed()) {
          stickerSet.view();
        }
        RelativeLayout contentView = (RelativeLayout) holder.itemView;
        View newView = contentView.getChildAt(0);
        NonMaterialButton button = (NonMaterialButton) contentView.getChildAt(1);
        TextView titleView = (TextView) contentView.getChildAt(2);
        TextView subtitleView = (TextView) contentView.getChildAt(3);

        contentView.setTag(stickerSet);

        newView.setVisibility(stickerSet == null || stickerSet.isViewed() ? View.GONE : View.VISIBLE);

        button.setInProgress(stickerSet != null && !stickerSet.isRecent() && isInProgress(stickerSet.getId()), false);
        button.setIsDone(stickerSet != null && stickerSet.isInstalled(), false);
        button.setTag(stickerSet);

        Views.setMediumText(titleView, stickerSet != null ? stickerSet.getTitle() : "");
        subtitleView.setText(stickerSet != null ? Lang.plural(R.string.xStickers, stickerSet.getSize()) : "");

        if (Views.setAlignParent(newView, Lang.rtl())) {
          int rightMargin = Screen.dp(6f);
          int topMargin = Screen.dp(3f);
          Views.setMargins(newView, Lang.rtl() ? rightMargin : 0, topMargin, Lang.rtl() ? 0 : rightMargin, 0);
          Views.updateLayoutParams(newView);
        }

        if (Views.setAlignParent(button, Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT)) {
          int leftMargin = Screen.dp(16f);
          int topMargin = Screen.dp(5f);
          Views.setMargins(button, Lang.rtl() ? 0 : leftMargin, topMargin, Lang.rtl() ? leftMargin : 0, 0);
          Views.updateLayoutParams(button);
        }

        RelativeLayout.LayoutParams params;
        params = (RelativeLayout.LayoutParams) titleView.getLayoutParams();
        if (Lang.rtl()) {
          int leftMargin = Screen.dp(12f);
          if (params.leftMargin != leftMargin) {
            params.leftMargin = leftMargin;
            params.rightMargin = 0;
            params.addRule(RelativeLayout.LEFT_OF, R.id.btn_new);
            params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_addStickerSet);
            Views.updateLayoutParams(titleView);
          }
        } else {
          int rightMargin = Screen.dp(12f);
          if (params.rightMargin != rightMargin) {
            params.rightMargin = rightMargin;
            params.leftMargin = 0;
            params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_new);
            params.addRule(RelativeLayout.LEFT_OF, R.id.btn_addStickerSet);
            Views.updateLayoutParams(titleView);
          }
        }
        Views.setTextGravity(titleView, Lang.gravity());

        if (Views.setAlignParent(subtitleView, Lang.rtl())) {
          Views.updateLayoutParams(subtitleView);
        }
        break;
      }
    }
  }

  @Override
  public void onViewAttachedToWindow (StickerHolder holder) {
    switch (holder.getItemViewType()) {
      case StickerHolder.TYPE_EMOJI_STATUS_DEFAULT:
      case StickerHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).attach();
        break;
      }
      case StickerHolder.TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).attach();
        break;
      }
    }
  }

  @Override
  public void onViewDetachedFromWindow (StickerHolder holder) {
    switch (holder.getItemViewType()) {
      case StickerHolder.TYPE_EMOJI_STATUS_DEFAULT:
      case StickerHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).detach();
        break;
      }
      case StickerHolder.TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).detach();
        break;
      }
    }
  }

  @Override
  public void onViewRecycled (StickerHolder holder) {
    switch (holder.getItemViewType()) {
      case StickerHolder.TYPE_EMOJI_STATUS_DEFAULT:
      case StickerHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).performDestroy();
        break;
      }
      case StickerHolder.TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).performDestroy();
        break;
      }
    }
  }

  public void addRange (int startIndex, ArrayList<StickerItem> items) {
    this.items.addAll(startIndex, items);
    notifyItemRangeInserted(startIndex, items.size());
  }

  public void insertRange (int index, ArrayList<StickerItem> items) {
    insertRange(index, items, true);
  }

  public void insertRange (int index, ArrayList<StickerItem> items, boolean notify) {
    this.items.addAll(index, items);
    if (notify) {
      notifyItemRangeInserted(index, items.size());
    }
  }

  public void removeRange (int startIndex, int count, boolean notify) {
    for (int i = startIndex + count - 1; i >= startIndex; i--) {
      items.remove(i);
    }
    if (notify) {
      notifyItemRangeRemoved(startIndex, count);
    }
  }

  public void removeRange (int startIndex, int count) {
    removeRange(startIndex, count, true);
  }

  public void moveRange (int fromIndex, int itemCount, int toIndex) {
    ArrayList<StickerItem> items = new ArrayList<>(itemCount);
    for (int i = fromIndex + itemCount - 1; i >= fromIndex; i--) {
      items.add(0, this.items.remove(i));
    }
    notifyItemRangeRemoved(fromIndex, itemCount);
    this.items.addAll(toIndex, items);
    notifyItemRangeInserted(toIndex, itemCount);
  }

  public StickerItem getItem (int index) {
    return items.get(index);
  }

  @Override
  public int getItemCount () {
    return items.size();
  }

  public int indexOfSticker (TGStickerObj sticker) {
    int i = 0;
    for (StickerItem item : items) {
      if (item.viewType == StickerHolder.TYPE_STICKER && sticker.equals(item.sticker)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public int indexOfSticker (TGStickerObj sticker, int startIndex) {
    if (startIndex == 0) {
      return indexOfSticker(sticker);
    } else {
      final int size = items.size();
      for (int i = startIndex; i < size; i++) {
        StickerItem item = items.get(i);
        if (item.viewType == StickerHolder.TYPE_STICKER && sticker.equals(item.sticker)) {
          return i;
        }
      }
    }
    return -1;
  }

  public static class StickerItem {
    public int viewType;
    public final TGStickerObj sticker;
    public final TGStickerSetInfo stickerSet;

    public StickerItem (int viewType) {
      this.viewType = viewType;
      this.sticker = null;
      this.stickerSet = null;
    }

    public StickerItem (int viewType, TGStickerObj sticker) {
      this.viewType = viewType;
      this.sticker = sticker;
      this.stickerSet = null;
    }

    public StickerItem (int viewType, TGStickerSetInfo info) {
      this.viewType = viewType;
      this.sticker = null;
      this.stickerSet = info;
    }

    public boolean setViewType (int viewType) {
      if (this.viewType != viewType) {
        this.viewType = viewType;
        return true;
      }
      return false;
    }
  }

  public int findSetIndexByPosition (int index) {
    return -1;
  }

  public void setItem (StickerItem item) {
    clear();
    if (item != null) {
      items.add(item);
      notifyItemInserted(0);
    }
  }

  private void clear () {
    if (!this.items.isEmpty()) {
      int count = this.items.size();
      this.items.clear();
      notifyItemRangeRemoved(0, count);
    }
  }

  public void setItems (ArrayList<StickerItem> items) {
    clear();
    if (items != null && !items.isEmpty()) {
      this.items.addAll(items);
      notifyItemRangeInserted(0, items.size());
    }
  }

  public void addItems (ArrayList<StickerItem> items) {
    if (items != null && !items.isEmpty()) {
      int index = this.items.size();
      this.items.addAll(items);
      notifyItemRangeInserted(index, items.size());
    }
  }

  @Override
  public int getItemViewType (int position) {
    return items.get(position).viewType;
  }

  public @Nullable TGStickerObj getSticker (int position) {
    return position >= 0 && position < items.size() ? items.get(position).sticker : null;
  }

  public @Nullable TGStickerSetInfo getStickerSet (int position) {
    return position >= 0 && position < items.size() ? items.get(position).stickerSet : null;
  }

  public static class StickerHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_STICKER = 0;
    public static final int TYPE_EMPTY = 1;
    public static final int TYPE_HEADER = 2;
    public static final int TYPE_HEADER_COLLAPSABLE = 3;
    public static final int TYPE_KEYBOARD_TOP = 4;
    public static final int TYPE_NO_STICKERSETS = 5;
    public static final int TYPE_PROGRESS = 6;
    public static final int TYPE_COME_AGAIN_LATER = 7;
    public static final int TYPE_HEADER_TRENDING = 8;
    public static final int TYPE_SEPARATOR = 10;
    public static final int TYPE_EMOJI_STATUS_DEFAULT = 11;
    public static final int TYPE_NO_EMOJISETS = 12;

    public StickerHolder (View itemView) {
      super(itemView);
    }

    public static @NonNull StickerHolder create (Context context, Tdlib tdlib, int viewType, boolean isTrending, View.OnClickListener onClickListener, StickerSmallView.StickerMovementCallback callback, boolean isBig, @Nullable ViewController<?> themeProvider) {
      switch (viewType) {
        case TYPE_EMOJI_STATUS_DEFAULT:
        case TYPE_STICKER: {
          StickerSmallView view;
          view = new StickerSmallView(context);
          view.init(tdlib);
          if (isTrending) {
            view.setIsTrending();
          }
          view.setStickerMovementCallback(callback);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          if (viewType == TYPE_EMOJI_STATUS_DEFAULT) {
            view.setIsPremiumStar();
          }
          return new StickerHolder(view);
        }
        case TYPE_EMPTY: {
          View view = new View(context);
          view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          return new StickerHolder(view);
        }
        case TYPE_HEADER: {
          TextView textView = new NoScrollTextView(context);
          textView.setTypeface(Fonts.getRobotoMedium());
          textView.setTextColor(Theme.textDecentColor());
          if (themeProvider != null) {
            themeProvider.addThemeTextDecentColorListener(textView);
          }
          textView.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
          textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          textView.setSingleLine(true);
          textView.setEllipsize(TextUtils.TruncateAt.END);
          textView.setPadding(Screen.dp(14f), Screen.dp(5f), Screen.dp(14f), Screen.dp(5f));
          textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(32f)));
          return new StickerHolder(textView);
        }
        case TYPE_HEADER_COLLAPSABLE: {
          LinearLayout viewGroup = new LinearLayout(context);
          viewGroup.setOrientation(LinearLayout.HORIZONTAL);
          viewGroup.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(32f)));
          for (int i = 0; i < 2; i++) {
            TextView textView = new NoScrollTextView(context);
            textView.setTextColor(Theme.textDecentColor());
            if (themeProvider != null) {
              themeProvider.addThemeTextDecentColorListener(textView);
            }
            textView.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setPadding(Screen.dp(14f), Screen.dp(5f), Screen.dp(14f), Screen.dp(5f));
            if (i == 0) {
              textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
              textView.setTypeface(Fonts.getRobotoMedium());
              textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            } else {
              textView.setId(R.id.btn_toggleCollapseRecentStickers);
              textView.setOnClickListener(onClickListener);
              textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
              textView.setTypeface(Fonts.getRobotoRegular());
              textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            viewGroup.addView(textView);
          }

          return new StickerHolder(viewGroup);
        }
        case TYPE_HEADER_TRENDING: {
          RelativeLayout contentView = new RelativeLayout(context);
          contentView.setOnClickListener(onClickListener);
          contentView.setPadding(Screen.dp(16f), Screen.dp(isBig ? 18f : 13f) - EmojiLayout.getHeaderPadding(), Screen.dp(16f), 0);
          contentView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(isBig ? 57f : 52f)));
          RelativeLayout.LayoutParams params;

          params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(16f));
          params.addRule(Lang.alignParent());
          if (Lang.rtl()) {
            params.leftMargin = Screen.dp(6f);
          } else {
            params.rightMargin = Screen.dp(6f);
          }
          params.topMargin = Screen.dp(3f);
          TextView newView = new NoScrollTextView(context);
          ViewSupport.setThemedBackground(newView, ColorId.promo, themeProvider).setCornerRadius(3f);
          newView.setId(R.id.btn_new);
          newView.setSingleLine(true);
          newView.setPadding(Screen.dp(4f), Screen.dp(1f), Screen.dp(4f), 0);
          newView.setTextColor(Theme.getColor(ColorId.promoContent));
          if (themeProvider != null) {
            themeProvider.addThemeTextColorListener(newView, ColorId.promoContent);
            themeProvider.addThemeInvalidateListener(newView);
          }
          newView.setTypeface(Fonts.getRobotoBold());
          newView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);
          newView.setText(Lang.getString(R.string.New).toUpperCase());
          newView.setLayoutParams(params);

          params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(28f));
          if (Lang.rtl()) {
            params.rightMargin = Screen.dp(16f);
          } else {
            params.leftMargin = Screen.dp(16f);
          }
          params.topMargin = Screen.dp(5f);
          params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
          NonMaterialButton button = new NonMaterialButton(context);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(button);
          }
          button.setId(R.id.btn_addStickerSet);
          button.setText(R.string.Add);
          button.setOnClickListener(onClickListener);
          button.setLayoutParams(params);

          params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
          if (Lang.rtl()) {
            params.leftMargin = Screen.dp(12f);
            params.addRule(RelativeLayout.LEFT_OF, R.id.btn_new);
            params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_addStickerSet);
          } else {
            params.rightMargin = Screen.dp(12f);
            params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_new);
            params.addRule(RelativeLayout.LEFT_OF, R.id.btn_addStickerSet);
          }
          TextView titleView = new NoScrollTextView(context);
          titleView.setTypeface(Fonts.getRobotoMedium());
          titleView.setTextColor(Theme.textAccentColor());
          titleView.setGravity(Lang.gravity());
          if (themeProvider != null) {
            themeProvider.addThemeTextAccentColorListener(titleView);
          }
          titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
          titleView.setSingleLine(true);
          titleView.setEllipsize(TextUtils.TruncateAt.END);
          titleView.setLayoutParams(params);

          params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
          params.addRule(Lang.alignParent());
          params.topMargin = Screen.dp(22f);
          TextView subtitleView = new NoScrollTextView(context);
          subtitleView.setTypeface(Fonts.getRobotoRegular());
          subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          subtitleView.setTextColor(Theme.textDecentColor());
          if (themeProvider != null) {
            themeProvider.addThemeTextDecentColorListener(subtitleView);
          }
          subtitleView.setSingleLine(true);
          subtitleView.setEllipsize(TextUtils.TruncateAt.END);
          subtitleView.setLayoutParams(params);

          contentView.addView(newView);
          contentView.addView(button);
          contentView.addView(titleView);
          contentView.addView(subtitleView);

          return new StickerHolder(contentView);
        }
        case TYPE_KEYBOARD_TOP: {
          View view = new View(context);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding()));
          return new StickerHolder(view);
        }
        case TYPE_SEPARATOR: {
          SeparatorView separatorView = new SeparatorView(context);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(separatorView);
          }
          separatorView.setAlignBottom();
          separatorView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(5f)));
          return new StickerHolder(separatorView);
        }
        case TYPE_COME_AGAIN_LATER:
        case TYPE_NO_EMOJISETS:
        case TYPE_NO_STICKERSETS: {
          TextView textView = new NoScrollTextView(context);
          textView.setTypeface(Fonts.getRobotoRegular());
          textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          textView.setTextColor(Theme.textDecentColor());
          if (themeProvider != null) {
            themeProvider.addThemeTextDecentColorListener(textView);
          }
          textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          textView.setSingleLine(true);
          textView.setText(Lang.getString(viewType == TYPE_NO_EMOJISETS ? R.string.NoEmojiSetsFound: viewType == TYPE_COME_AGAIN_LATER ? R.string.ComeAgainLater : R.string.NoStickerSets));
          textView.setGravity(Gravity.CENTER);
          textView.setEllipsize(TextUtils.TruncateAt.END);
          //noinspection ResourceType
          textView.setPadding(Screen.dp(14f), isBig ? 0 : EmojiLayout.getHeaderSize(), Screen.dp(14f), 0);
          return new StickerHolder(textView);
        }
        case TYPE_PROGRESS: {
          ProgressComponentView progressView = new ProgressComponentView(context);
          progressView.initBig(1f);
          //noinspection ResourceType
          progressView.setPadding(0, isBig ? 0 : EmojiLayout.getHeaderSize(), 0, 0);
          progressView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new StickerHolder(progressView);
        }
      }
      throw new UnsupportedOperationException("viewType == " + viewType);
    }
  }
}
