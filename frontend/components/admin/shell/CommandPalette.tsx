'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { NAV_GROUPS } from './nav-config';
import {
  CommandDialog,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandSeparator,
} from '@/components/ui/command';
import { api } from '@/lib/api/client';

interface UserSearchResult {
  id: string;
  email: string;
  nickname: string;
}

export function CommandPalette() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [searchInput, setSearchInput] = useState('');
  const [searchResults, setSearchResults] = useState<UserSearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const searchTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Global keyboard shortcut (Cmd+K or Ctrl+K)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setOpen((prev) => !prev);
      }
      if (e.key === 'Escape' && open) {
        setOpen(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open]);

  // Debounced user search
  const performSearch = useCallback(async (query: string) => {
    if (!query.trim()) {
      setSearchResults([]);
      return;
    }

    setSearching(true);
    try {
      const res = await api.get<UserSearchResult[]>('/api/admin/users/search', {
        params: { q: query },
      });
      setSearchResults(res.data || []);
    } catch (error) {
      console.error('User search failed:', error);
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  }, []);

  useEffect(() => {
    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);

    if (!searchInput.trim()) {
      setSearchResults([]);
      setSearching(false);
      return;
    }

    searchTimeoutRef.current = setTimeout(() => {
      performSearch(searchInput);
    }, 300);

    return () => {
      if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    };
  }, [searchInput, performSearch]);

  const handleSelectPage = (href: string) => {
    setOpen(false);
    setSearchInput('');
    setSearchResults([]);
    router.push(href);
  };

  const handleSelectUser = (userId: string, email: string) => {
    setOpen(false);
    setSearchInput('');
    setSearchResults([]);
    router.push(`/admin/users?search=${encodeURIComponent(email)}`);
  };

  return (
    <CommandDialog open={open} onOpenChange={setOpen}>
      <CommandInput
        placeholder="페이지 이동 또는 회원 검색..."
        value={searchInput}
        onValueChange={setSearchInput}
        data-testid="admin-command-palette"
      />
      <CommandList>
        <CommandEmpty>검색 결과가 없습니다.</CommandEmpty>

        {/* Pages section */}
        {NAV_GROUPS.map((group) => {
          const items = group.items.filter((item) =>
            !searchInput.trim() || item.label.toLowerCase().includes(searchInput.toLowerCase())
          );

          if (items.length === 0) return null;

          return (
            <CommandGroup key={group.label} heading={group.label}>
              {items.map((item) => (
                <CommandItem
                  key={item.href}
                  onSelect={() => handleSelectPage(item.href)}
                  value={item.label}
                >
                  <span className="text-sm">{item.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          );
        })}

        {/* Search results separator */}
        {searchInput.trim() && searchResults.length > 0 && (
          <>
            <CommandSeparator />
            <CommandGroup heading="회원">
              {searching && <div className="py-2 text-xs text-gray-500 text-center">검색 중...</div>}
              {!searching &&
                searchResults.map((user) => (
                  <CommandItem
                    key={user.id}
                    onSelect={() => handleSelectUser(user.id, user.email)}
                    value={user.email}
                  >
                    <div className="flex flex-col">
                      <span className="text-sm font-medium">{user.nickname}</span>
                      <span className="text-xs text-gray-500">{user.email}</span>
                    </div>
                  </CommandItem>
                ))}
            </CommandGroup>
          </>
        )}
      </CommandList>
    </CommandDialog>
  );
}
