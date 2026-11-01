package com.xinyv.median;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** URL-only hand-off. Browser cookies, authorization and private profile data never leave Median. */
final class ExternalDownloadIntents {
    private ExternalDownloadIntents() {}

    static ArrayList<Intent> candidates(Context context, String url) throws Exception {
        Uri target = Uri.parse(NetworkSecurity.parseHttpUrl(url).toString());
        ArrayList<Intent> result = new ArrayList<Intent>();
        Set<String> seen = new HashSet<String>();

        Intent typedView = new Intent(Intent.ACTION_VIEW);
        typedView.setDataAndType(target, "application/octet-stream");
        typedView.putExtra(Intent.EXTRA_TITLE, "Median 下载");
        addHandlers(context, typedView, seen, result);

        // Some downloaders accept only shared text. Include them even when browsers
        // can handle ACTION_VIEW; a browser handler must not suppress this route.
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, target.toString());
        share.putExtra(Intent.EXTRA_SUBJECT, "Median 下载");
        share.putExtra(Intent.EXTRA_TITLE, "Median 下载");
        addHandlers(context, share, seen, result);

        Intent view = new Intent(Intent.ACTION_VIEW, target);
        view.putExtra(Intent.EXTRA_TITLE, "Median 下载");
        addHandlers(context, view, seen, result);
        return result;
    }

    private static void addHandlers(Context context, Intent source, Set<String> seen, List<Intent> result) {
        PackageManager manager = context.getPackageManager();
        for (ResolveInfo info : manager.queryIntentActivities(source, PackageManager.MATCH_DEFAULT_ONLY)) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null || !activity.enabled || !activity.exported ||
                    context.getPackageName().equals(activity.packageName) ||
                    MainActivity.class.getName().equals(activity.name)) continue;
            if (activity.permission != null && context.checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED)
                continue;
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            if (!seen.add(component.flattenToString())) continue;
            Intent intent = new Intent(source);
            intent.setComponent(component);
            result.add(intent);
        }
    }

    static String[] labels(Context context, List<Intent> candidates) {
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            ComponentName component = candidates.get(i).getComponent();
            String label = component == null ? "外部应用" : component.getPackageName();
            if (component != null) try {
                ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
                CharSequence name = info.loadLabel(context.getPackageManager());
                if (name != null && name.length() > 0) label = name.toString();
            } catch (PackageManager.NameNotFoundException ignored) {}
            labels[i] = label;
        }
        return labels;
    }
}
