package com.gittest.proj.ui.activity;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static com.github.mobile.Intents.EXTRA_REPOSITORY;
import static com.github.mobile.ResultCodes.RESOURCE_CHANGED;
import static com.github.mobile.util.TypefaceUtils.ICON_CODE;
import static com.github.mobile.util.TypefaceUtils.ICON_COMMIT;
import static com.github.mobile.util.TypefaceUtils.ICON_ISSUE_OPEN;
import static com.github.mobile.util.TypefaceUtils.ICON_NEWS;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ProgressBar;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.github.kevinsawicki.wishlist.ViewUtils;
import com.github.mobile.Intents.Builder;
import com.gittest.proj.R.id;
import com.gittest.proj.R.layout;
import com.gittest.proj.R.menu;
import com.gittest.proj.R.string;
import com.github.mobile.core.repo.RefreshRepositoryTask;
import com.github.mobile.core.repo.RepositoryUtils;
import com.github.mobile.core.repo.StarRepositoryTask;
import com.github.mobile.core.repo.StarredRepositoryTask;
import com.github.mobile.core.repo.UnstarRepositoryTask;
import com.github.mobile.ui.TabPagerActivity;
import com.github.mobile.ui.repo.RepositoryPagerAdapter;
import com.github.mobile.util.AvatarLoader;
import com.github.mobile.util.ToastUtils;
import com.google.inject.Inject;
import com.github.mobile.util.ShareUtils;
import com.gittest.proj.ui.activity.HomeActivity;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;

public class RepositoryViewActivity extends
        TabPagerActivity<RepositoryPagerAdapter> {

    public static Intent createIntent(Repository repository) {
        return new Builder("repo.VIEW").repo(repository).toIntent();
    }

    private Repository repository;

    @Inject
    private AvatarLoader avatars;

    private ProgressBar loadingBar;

    private boolean isStarred;

    private boolean starredStatusChecked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = getSerializableExtra(EXTRA_REPOSITORY);

        loadingBar = finder.find(id.pb_loading);

        User owner = repository.getOwner();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(repository.getName());
        actionBar.setSubtitle(owner.getLogin());
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (owner.getAvatarUrl() != null
                && RepositoryUtils.isComplete(repository))
            configurePager();
        else {
            avatars.bind(getSupportActionBar(), owner);
            ViewUtils.setGone(loadingBar, false);
            setGone(true);
            new RefreshRepositoryTask(this, repository) {

                @Override
                protected void onSuccess(Repository fullRepository)
                        throws Exception {
                    super.onSuccess(fullRepository);

                    repository = fullRepository;
                    configurePager();
                }

                @Override
                protected void onException(Exception e) throws RuntimeException {
                    super.onException(e);

                    ToastUtils.show(RepositoryViewActivity.this,
                            string.error_repo_load);
                    ViewUtils.setGone(loadingBar, true);
                }
            }.execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu optionsMenu) {
        getSupportMenuInflater().inflate(menu.repository, optionsMenu);
        return super.onCreateOptionsMenu(optionsMenu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem followItem = menu.findItem(id.m_star);

        followItem.setVisible(starredStatusChecked);
        followItem.setTitle(isStarred ? string.unstar : string.star);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onSearchRequested() {
        if (pager.getCurrentItem() == 1) {
            Bundle args = new Bundle();
            args.putSerializable(EXTRA_REPOSITORY, repository);
            startSearch(null, false, args, false);
            return true;
        } else
            return false;
    }

    private void configurePager() {
        avatars.bind(getSupportActionBar(), repository.getOwner());
        configureTabPager();
        ViewUtils.setGone(loadingBar, true);
        setGone(false);
        checkStarredRepositoryStatus();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case id.m_star:
            starRepository();
            return true;
        case id.m_share:
            shareRepository();
            return true;
        case android.R.id.home:
            finish();
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onDialogResult(int requestCode, int resultCode, Bundle arguments) {
        adapter.onDialogResult(pager.getCurrentItem(), requestCode, resultCode,
                arguments);
    }

    @Override
    protected RepositoryPagerAdapter createAdapter() {
        return new RepositoryPagerAdapter(this, repository.isHasIssues());
    }

    @Override
    protected int getContentView() {
        return layout.tabbed_progress_pager;
    }

    @Override
    protected String getIcon(int position) {
        switch (position) {
        case 0:
            return ICON_NEWS;
        case 1:
            return ICON_CODE;
        case 2:
            return ICON_COMMIT;
        case 3:
            return ICON_ISSUE_OPEN;
        default:
            return super.getIcon(position);
        }
    }

    private void starRepository() {
        if (isStarred)
            new UnstarRepositoryTask(this, repository) {

                @Override
                protected void onSuccess(Void v) throws Exception {
                    super.onSuccess(v);

                    isStarred = !isStarred;
                    setResult(RESOURCE_CHANGED);
                }

                @Override
                protected void onException(Exception e) throws RuntimeException {
                    super.onException(e);

                    ToastUtils.show(RepositoryViewActivity.this,
                            string.error_unstarring_repository);
                }
            }.start();
        else
            new StarRepositoryTask(this, repository) {

                @Override
                protected void onSuccess(Void v) throws Exception {
                    super.onSuccess(v);

                    isStarred = !isStarred;
                    setResult(RESOURCE_CHANGED);
                }

                @Override
                protected void onException(Exception e) throws RuntimeException {
                    super.onException(e);

                    ToastUtils.show(RepositoryViewActivity.this,
                            string.error_starring_repository);
                }
            }.start();
    }

    private void checkStarredRepositoryStatus() {
        starredStatusChecked = false;
        new StarredRepositoryTask(this, repository) {

            @Override
            protected void onSuccess(Boolean watching) throws Exception {
                super.onSuccess(watching);

                isStarred = watching;
                starredStatusChecked = true;
                invalidateOptionsMenu();
            }
        }.execute();
    }

    private void shareRepository() {
        String repoUrl = repository.getHtmlUrl();
        if (TextUtils.isEmpty(repoUrl))
          repoUrl = "https://github.com/" + repository.generateId();
        Intent sharingIntent = ShareUtils.create(repository.generateId(),
                                                 repoUrl);
        startActivity(sharingIntent);
    }
}