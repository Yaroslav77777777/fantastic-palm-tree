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
 * File created on 21/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.net.Uri;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.IntDef;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.FillingDecoration;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;
import me.vkryl.td.TdConstants;

public class EditUsernameController extends EditBaseController<EditUsernameController.Args> implements SettingsAdapter.TextChangeListener, View.OnClickListener {
  public static class Args {
    public long chatId;

    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  public EditUsernameController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long chatId;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.chatId = args.chatId;
  }

  @Override
  public int getId () {
    return R.id.controller_editUsername;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(chatId != 0 ? (tdlib.isChannel(chatId) ? R.string.ChannelLink : R.string.GroupLink) : R.string.Username);
  }

  private SettingsAdapter adapter;
  private String sourceUsername, currentUsername;
  private ListItem checkedItem, checkingItem, description;

  @Override
  protected int getRecyclerBackgroundColorId () {
    return chatId != 0 ? ColorId.background : super.getRecyclerBackgroundColorId();
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    TdApi.Usernames usernames;
    if (chatId != 0) {
      usernames = tdlib.chatUsernames(chatId);
    } else {
      usernames = tdlib.myUserUsernames();
    }
    sourceUsername = currentUsername = usernames != null ? usernames.editableUsername : "";

    checkingItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, chatId != 0 ? R.string.LinkChecking : R.string.UsernameChecking).setTextColorId(ColorId.textLight);
    checkedItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.state, 0, 0);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
      }
    };
    adapter.setTextChangeListener(this);
    adapter.setLockFocusOn(this, true);

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EDITTEXT, R.id.input, 0, chatId != 0 ? tdlib.tMeHost() : Lang.getString(R.string.Username), false).setStringValue(currentUsername)
      .setInputFilters(new InputFilter[] {
        new CodePointCountFilter(TdConstants.MAX_USERNAME_LENGTH),
        new TD.UsernameFilter()
      }).setOnEditorActionListener(new SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this)));
    items.add((description = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, genDescription(), false)
      .setTextColorId(ColorId.textLight)));

    if (chatId != 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, R.id.shadowBottom));
      recyclerView.addItemDecoration(new FillingDecoration(recyclerView, this).addBottom(R.id.shadowBottom, items.size()));
    }

    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  @Override
  public void onTextChanged (int id, ListItem ite, MaterialEditTextGroup v, String username) {
    if (currentUsername.equals(username)) {
      return;
    }
    doUsernameCheck(username);
  }

  private void doUsernameCheck (String username) {
    this.currentUsername = username;
    cancelCheckRequest();

    if (description.setStringIfChanged(genDescription())) {
      adapter.updateSimpleItemById(R.id.description);
    }

    if (sourceUsername.equals(username) && !username.isEmpty()) {
      checkedItem.setTextColorId(ColorId.textSecure);
      checkedItem.setString(getResultString(ResultType.AVAILABLE));
      adapter.updateEditTextById(R.id.input, true, false);
      setState(STATE_CHECKED);
    } else {
      adapter.updateEditTextById(R.id.input, false, false);
      setState(username.length() < MIN_USERNAME_LENGTH || !TD.matchUsername(username) || username.length() > 32 ? STATE_NONE : STATE_CHECKING);
    }
    if (state == STATE_CHECKING) {
      checkUsername();
    }
  }

  private CancellableRunnable checkRunnable;

  private void checkUsername () {
    checkRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        checkUsernameInternal(checkRunnable);
      }
    };
    checkRunnable.removeOnCancel(UI.getAppHandler());
    UI.post(checkRunnable, 350l);
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.chat) {
      ListItem item = (ListItem) v.getTag();
      if (item == null) {
        return;
      }
      TGFoundChat chat = (TGFoundChat) item.getData();
      if (chat == null) {
        return;
      }
      String publicLink = tdlib.tMeHost() + tdlib.chatUsername(chat.getChatId());
      showOptions(publicLink, new int[] {R.id.btn_delete, R.id.btn_openChat}, new String[] {Lang.getString(R.string.ChatLinkRemove), Lang.getString(R.string.ChatLinkView)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_visibility_24}, (itemView, id) -> {
        if (id == R.id.btn_openChat) {
          tdlib.ui().openChat(this, chat.getChatId(), new TdlibUi.ChatOpenParameters().keepStack());
        } else if (id == R.id.btn_delete) {
          showOptions(Lang.getStringBold(R.string.ChatLinkRemoveAlert, tdlib.chatTitle(chat.getChatId()), publicLink), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ChatLinkRemove), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (resultItemView, confirmId) -> {
            if (confirmId == R.id.btn_delete && !isInProgress()) {
              setInProgress(true);
              tdlib.client().send(new TdApi.SetSupergroupUsername(ChatId.toSupergroupId(chat.getChatId()), null), result -> {
                switch (result.getConstructor()) {
                  case TdApi.Ok.CONSTRUCTOR: {
                    tdlib.ui().post(() -> {
                      if (!isDestroyed()) {
                        setInProgress(false);
                        doUsernameCheck(currentUsername);
                        Keyboard.show(getLockFocusView());
                      }
                    });
                    break;
                  }
                  case TdApi.Error.CONSTRUCTOR: {
                    UI.showError(result);
                    tdlib.ui().post(() -> {
                      if (!isDestroyed()) {
                        setInProgress(false);
                      }
                    });
                    break;
                  }
                }
              });
            }
            return true;
          });
        }
        return true;
      });
    }
  }

  private void cancelCheckRequest () {
    if (checkRunnable != null) {
      checkRunnable.cancel();
      checkRunnable = null;
    }
    setPublicChats(null);
  }

  private void checkUsernameInternal (final CancellableRunnable runnable) {
    final String username = currentUsername;
    tdlib.client().send(new TdApi.CheckChatUsername(ChatId.isBasicGroup(chatId) ? 0 : chatId != 0 ? chatId : tdlib.selfChatId(), username), object -> tdlib.ui().post(() -> {
      if (!isDestroyed() && currentUsername.equals(username)) {
        boolean isAvailable = false;
        CharSequence result;
        boolean needOccupiedList = false;
        switch (object.getConstructor()) {
          case TdApi.CheckChatUsernameResultOk.CONSTRUCTOR:
            isAvailable = true;
            result = getResultString(ResultType.AVAILABLE);
            break;
          case TdApi.CheckChatUsernameResultUsernameOccupied.CONSTRUCTOR:
            result = getResultString(ResultType.OCCUPIED);
            break;

          case TdApi.CheckChatUsernameResultUsernamePurchasable.CONSTRUCTOR:
            result = getResultString(ResultType.PURCHASABLE);
            break;
          case TdApi.CheckChatUsernameResultPublicChatsTooMany.CONSTRUCTOR:
            result = Lang.getString(R.string.TooManyPublicLinks);
            needOccupiedList = true;
            break;
          case TdApi.CheckChatUsernameResultUsernameInvalid.CONSTRUCTOR:
            result = Lang.getString(chatId != 0 ? R.string.LinkInvalid : R.string.UsernameInvalid);
            break;
          case TdApi.CheckChatUsernameResultPublicGroupsUnavailable.CONSTRUCTOR:
            result = Lang.getString(R.string.PublicGroupsUnavailable);
            break;
          case TdApi.Error.CONSTRUCTOR:
            result = TD.toErrorString(object);
            break;
          default:
            throw new RuntimeException();
        }

        checkedItem.setString(result);
        checkedItem.setTextColorId(isAvailable ? ColorId.textSecure : ColorId.textNegative);
        setState(STATE_CHECKED);
        adapter.updateEditTextById(R.id.input, isAvailable, !isAvailable);

        if (needOccupiedList && chatId != 0) {
          tdlib.client().send(new TdApi.GetCreatedPublicChats(), publicChats -> {
            if (publicChats.getConstructor() != TdApi.Chats.CONSTRUCTOR) {
              return;
            }
            List<TdApi.Chat> chats = tdlib.chats(((TdApi.Chats) publicChats).chatIds);
            if (chats.isEmpty()) {
              return;
            }
            final List<TGFoundChat> foundChats = new ArrayList<>(chats.size());
            for (TdApi.Chat chat : chats) {
              TGFoundChat foundChat = new TGFoundChat(tdlib, null, chat, true, null);
              foundChat.setNoUnread();
              foundChat.setUseTme();
              foundChats.add(foundChat);
            }
            tdlib.ui().post(() -> {
              if (!isDestroyed() && checkRunnable == runnable && runnable.isPending()) {
                setPublicChats(foundChats);
              }
            });
          });
        }
      }
    }));
  }

  private List<TGFoundChat> occupiedChats;

  private void setPublicChats (List<TGFoundChat> chats) {
    if (occupiedChats == null && chats == null) {
      return;
    }
    if (this.occupiedChats != null) {
      int i = adapter.indexOfViewById(R.id.occupiedChats);
      if (i != -1) {
        adapter.removeRange(i, 1 + occupiedChats.size() * 2);
        // fillingDecoration.clearRanges();
      }
    }
    this.occupiedChats = chats;
    if (chats != null) {
      List<ListItem> items = adapter.getItems();
      int i = items.size();
      boolean first = true;
      for (TGFoundChat chat : chats) {
        if (first) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, R.id.occupiedChats));
          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.chat).setLongId(chat.getId()).setData(chat));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      // fillingDecoration.addRange(i + 1, items.size() - 1);
      adapter.notifyItemRangeInserted(i, items.size() - i);
    }
    recyclerView.invalidateItemDecorations();
  }

  @Override
  protected void onProgressStateChanged (boolean inProgress) {
    adapter.updateLockEditTextById(R.id.input, inProgress ? currentUsername : null);
  }

  private static final int MIN_USERNAME_LENGTH = 1;

  @Override
  protected boolean onDoneClick () {
    if (currentUsername.isEmpty()) {
      setUsername("");
    } else if (currentUsername.length() < MIN_USERNAME_LENGTH) {
      showError(Lang.getString(chatId != 0 ? R.string.LinkInvalidShort : R.string.UsernameInvalidShort));
    } else if (currentUsername.length() > TdConstants.MAX_USERNAME_LENGTH) {
      showError(Lang.getString(chatId != 0 ? R.string.LinkInvalidLong : R.string.UsernameInvalidLong));
    } else if (StringUtils.isNumeric(currentUsername.charAt(0))) {
      showError(Lang.getString(chatId != 0 ? R.string.LinkInvalidStartNumber : R.string.UsernameInvalidStartNumber));
    } else if (!TD.matchUsername(currentUsername)) {
      showError(Lang.getString(chatId != 0 ? R.string.LinkInvalid : R.string.UsernameInvalid));
    } else {
      setUsername(currentUsername);
    }
    return true;
  }

  private void setUsername (final String username) {
    setUsername(username, true);
  }

  private void setUsername (final String username, boolean needPrompt) {
    if (!isInProgress()) {
      if (ChatId.isBasicGroup(chatId) && needPrompt) {
        if (StringUtils.isEmpty(username)) {
          onSaveCompleted();
        } else {
          showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), () -> setUsername(username, false));
        }
        return;
      }
      setInProgress(true);

      Client.ResultHandler handler = object -> {
        final boolean isOk = object.getConstructor() == TdApi.Ok.CONSTRUCTOR || (object.getConstructor() == TdApi.Error.CONSTRUCTOR && "USERNAME_NOT_MODIFIED".equals(((TdApi.Error) object).message));
        tdlib.ui().post(() -> {
          setInProgress(false);
          if (!isDestroyed()) {
            if (isOk) {
              onSaveCompleted();
            } else {
              showError(TD.toErrorString(object));
            }
          }
        });
      };

      if (chatId != 0) {
        if (ChatId.isBasicGroup(chatId)) {
          tdlib.upgradeToSupergroup(chatId, (oldChatId, newChatId, error) -> {
            if (newChatId != 0) {
              setArguments(new Args(newChatId));
              tdlib.client().send(new TdApi.SetSupergroupUsername(ChatId.toSupergroupId(chatId), username), handler);
            } else {
              handler.onResult(error != null ? error : new TdApi.Error(-1, "Failed to upgrade to supergroup"));
            }
          });
        } else {
          tdlib.client().send(new TdApi.SetSupergroupUsername(ChatId.toSupergroupId(chatId), username), handler);
        }
      } else {
        tdlib.client().send(new TdApi.SetUsername(username), handler);
      }
    }
  }

  private void showError (String error) {
    checkedItem.setString(error);
    checkedItem.setTextColorId(ColorId.textNegative);
    adapter.updateEditTextById(R.id.input, false, true);
    setState(STATE_CHECKED);
  }

  private static final int STATE_NONE = 0;
  private static final int STATE_CHECKING = 1;
  private static final int STATE_CHECKED = 2;

  private int state;

  private void setState (int state) {
    if (this.state != state) {
      int prevState = this.state;
      this.state = state;

      if (prevState != STATE_NONE && state != STATE_NONE) {
        adapter.setItem(1, state == STATE_CHECKED ? checkedItem : checkingItem);
      } else if (prevState == STATE_NONE) {
        adapter.addItem(1, state == STATE_CHECKED ? checkedItem : checkingItem);
      } else {
        adapter.removeItem(1);
      }
    } else if (state == STATE_CHECKED) {
      adapter.updateSimpleItemById(checkedItem.getId());
    }
  }

  private CharSequence helpSequence;

  private CharSequence genDescription () {
    if (helpSequence == null) {
      helpSequence = Strings.replaceBoldTokens(Lang.getString(chatId != 0 ? (tdlib.isChannel(chatId) ? R.string.LinkChannelHelp : R.string.LinkGroupHelp) : R.string.UsernameHelp), ColorId.textLight);
    }
    if (currentUsername.length() >= MIN_USERNAME_LENGTH && currentUsername.length() <= 32 && chatId == 0) {
      SpannableStringBuilder b = new SpannableStringBuilder(helpSequence);
      b.append("\n\n");
      b.append(Lang.getString(currentUsername.equals(sourceUsername) ? R.string.ThisLinkOpens : R.string.ThisLinkWillOpen));
      b.append(" ");
      String tMeUrl = tdlib.tMeUrl(currentUsername);
      b.append(tMeUrl);
      b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoRegular(), ColorId.textLink), b.length() - tMeUrl.length(), b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return b;
    }
    return helpSequence;
  }

  @IntDef({
    ResultType.AVAILABLE,
    ResultType.OCCUPIED,
    ResultType.PURCHASABLE
  })
  private @interface ResultType {
    int AVAILABLE = 0, OCCUPIED = 1, PURCHASABLE = 2;
  }

  private CharSequence getResultString (@ResultType int resultType) {
    switch (resultType) {
      case ResultType.AVAILABLE: {
        SpannableStringBuilder b = new SpannableStringBuilder(Lang.getString(sourceUsername.equals(currentUsername) ? (chatId != 0 ? R.string.LinkCurrent : R.string.UsernameCurrent) : R.string.UsernameAvailable, currentUsername));
        b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), ColorId.textSecure), 0, currentUsername.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
      }
      case ResultType.OCCUPIED: {
        return Lang.getString(chatId != 0 ? R.string.LinkInUse : R.string.UsernameInUse);
      }
      case ResultType.PURCHASABLE: {
        String learnMoreUrl = new Uri.Builder()
          .scheme("https")
          .authority(TdConstants.FRAGMENT_HOST)
          .path("username/" + currentUsername)
          .build()
          .toString();
        return Lang.getMarkdownStringSecure(this, chatId != 0 ? R.string.LinkPurchasable : R.string.UsernamePurchasable, learnMoreUrl);
      }
      default: {
        throw new IllegalArgumentException(Integer.toString(resultType));
      }
    }
  }

}
