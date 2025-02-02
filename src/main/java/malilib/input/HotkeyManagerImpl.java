package malilib.input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class HotkeyManagerImpl implements HotkeyManager
{
    protected final List<HotkeyCategory> keyBindCategories = new ArrayList<>();
    protected final List<HotkeyProvider> keyBindProviders = new ArrayList<>();
    protected Int2ObjectOpenHashMap<ArrayList<KeyBind>> hotkeyMap = new Int2ObjectOpenHashMap<>();
    @Nullable protected ImmutableList<HotkeyCategory> immutableKeyBindCategories;

    @Override
    public void registerHotkeyProvider(HotkeyProvider provider)
    {
        if (this.keyBindProviders.contains(provider) == false)
        {
            this.keyBindProviders.add(provider);
        }

        for (HotkeyCategory category : provider.getHotkeysByCategories())
        {
            this.addKeyBindCategory(category);
        }
    }

    @Override
    public void unregisterHotkeyProvider(HotkeyProvider provider)
    {
        this.keyBindProviders.remove(provider);
    }

    @Override
    public ImmutableList<HotkeyCategory> getHotkeyCategories()
    {
        if (this.immutableKeyBindCategories == null)
        {
            this.immutableKeyBindCategories = ImmutableList.copyOf(this.keyBindCategories);
        }

        return this.immutableKeyBindCategories;
    }

    @Override
    public void updateUsedKeys()
    {
        // Create a new map to avoid a CME in checkKeyBindsForChanges(),
        // if the update is triggered from a keybind callback
        Int2ObjectOpenHashMap<ArrayList<KeyBind>> hotkeyMap = new Int2ObjectOpenHashMap<>();

        for (HotkeyProvider handler : this.keyBindProviders)
        {
            for (Hotkey hotkey : handler.getAllHotkeys())
            {
                this.addKeyBindToMap(hotkey.getKeyBind(), hotkeyMap);
            }
        }

        hotkeyMap.values().forEach((list) -> list.sort(Comparator.comparingInt((v) -> v.getSettings().getPriority())));
        this.hotkeyMap = hotkeyMap;
    }

    protected void addKeyBindToMap(KeyBind keybind, Int2ObjectOpenHashMap<ArrayList<KeyBind>> hotkeyMap)
    {
        IntArrayList keys = new IntArrayList();
        keybind.getKeysToList(keys);
        final int size = keys.size();

        for (int i = 0; i < size; ++i)
        {
            int key = keys.getInt(i);
            hotkeyMap.computeIfAbsent(key, (k) -> new ArrayList<>()).add(keybind);
        }
    }

    protected void addKeyBindCategory(HotkeyCategory category)
    {
        // Remove a previous entry, if any (matched based on the modName and keyCategory only!)
        this.keyBindCategories.remove(category);
        this.keyBindCategories.add(category);
        this.immutableKeyBindCategories = null; // mark for rebuild
    }

    /**
     * NOT PUBLIC API - DO NOT CALL FROM MOD CODE
     */
    boolean checkKeyBindsForChanges(int eventKey)
    {
        boolean cancel = false;
        boolean isFirst = true;
        List<KeyBind> keyBinds = this.hotkeyMap.get(eventKey);

        if (keyBinds != null && keyBinds.isEmpty() == false)
        {
            for (KeyBind keyBind : keyBinds)
            {
                // Note: updateIsPressed() has to be called for key releases too, to reset the state
                KeyUpdateResult result = keyBind.updateIsPressed(isFirst);

                if (result.triggered)
                {
                    isFirst = false;
                }

                cancel |= result.cancel;
            }
        }

        return cancel;
    }
}
