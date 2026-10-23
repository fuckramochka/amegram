package app.miogram.bridge.wall;

import android.content.Context;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.wall.WallModels.Wall;
import app.miogram.bridge.wall.WallModels.WallElement;

/**
 * Адаптер стіни: masonry-сітка (StaggeredGrid), фільтри за типами,
 * закріплені елементи — першими, full-span для великих елементів.
 */
public class WallAdapter extends RecyclerListView.SelectionAdapter {

    public interface Listener {
        void onElementClick(WallElement element);

        void onLike(WallElement element, boolean like);

        void onComments(WallElement element);

        void onEdit(WallElement element);

        void onDelete(WallElement element);

        void onVote(WallElement element, int value);

        void onMediaClick(WallElement element);

        void onAction(WallElement element);

        void onOpenHtml(WallElement element);
    }

    private final Context context;
    private final Listener listener;
    private BaseFragment parentFragment;
    private Wall wall;
    private String filter = "all";
    private long myId;
    private final List<WallElement> items = new ArrayList<>();
    private int totalCount;

    public WallAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setParentFragment(BaseFragment fragment) {
        this.parentFragment = fragment;
    }

    public void setMyId(long myId) {
        this.myId = myId;
    }

    public void setWall(Wall wall, String filter) {
        this.wall = wall;
        this.filter = filter == null ? "all" : filter;
        rebuild();
        notifyDataSetChanged();
    }

    public void rebind(WallElement element) {
        if (element == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(element.id)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    public WallElement itemAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    public int realCount() {
        return totalCount;
    }

    private void rebuild() {
        items.clear();
        totalCount = wall == null || wall.elements == null ? 0 : wall.elements.size();
        if (wall == null || wall.elements == null) return;
        List<WallElement> pinned = new ArrayList<>();
        List<WallElement> rest = new ArrayList<>();
        for (WallElement e : wall.elements) {
            if (matchesFilter(e)) {
                if (e.pinned) pinned.add(e); else rest.add(e);
            }
        }
        items.addAll(pinned);
        items.addAll(rest);
    }

    private boolean matchesFilter(WallElement e) {
        switch (filter) {
            case WallModels.TYPE_TEXT:
                return WallModels.TYPE_TEXT.equals(e.type) || WallModels.TYPE_FORWARD.equals(e.type)
                        || WallModels.TYPE_STICKER.equals(e.type);
            case "media":
                return WallModels.TYPE_PHOTO.equals(e.type) || WallModels.TYPE_GIF.equals(e.type)
                        || WallModels.TYPE_VIDEO.equals(e.type) || WallModels.TYPE_FILE.equals(e.type);
            case WallModels.TYPE_POLL:
                return WallModels.TYPE_POLL.equals(e.type);
            case WallModels.TYPE_DRAWING:
                return WallModels.TYPE_DRAWING.equals(e.type);
            case WallModels.TYPE_BUTTON:
                return WallModels.TYPE_BUTTON.equals(e.type);
            case WallModels.TYPE_HTML:
                return WallModels.TYPE_HTML.equals(e.type);
            case "all":
            default:
                return true;
        }
    }

    private boolean isFullSpan(WallElement e) {
        if (e == null) return false;
        if (WallModels.TYPE_POLL.equals(e.type) || WallModels.TYPE_DRAWING.equals(e.type)) {
            return true;
        }
        return "full".equals(e.size);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        WallCardView card = new WallCardView(context);
        card.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(card);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        WallCardView card = (WallCardView) holder.itemView;
        RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) card.getLayoutParams();
        if (lp instanceof StaggeredGridLayoutManager.LayoutParams) {
            ((StaggeredGridLayoutManager.LayoutParams) lp).setFullSpan(isFullSpan(items.get(position)));
            card.setLayoutParams(lp);
        }
        card.bind(items.get(position), wall, myId, listener, parentFragment);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return true;
    }
}
