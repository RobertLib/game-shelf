// Mirrors supabase/migrations/*.sql in the format produced by
// `npx supabase gen types typescript`. Regenerate after schema changes.

export type Json = string | number | boolean | null | { [key: string]: Json | undefined } | Json[];

export type Database = {
  __InternalSupabase: {
    PostgrestVersion: '13.0.5';
  };
  public: {
    Tables: {
      game_images: {
        Row: {
          created_at: string;
          game_id: string;
          height: number | null;
          id: string;
          position: number;
          storage_path: string;
          thumb_path: string;
          user_id: string;
          width: number | null;
        };
        Insert: {
          created_at?: string;
          game_id: string;
          height?: number | null;
          id?: string;
          position?: number;
          storage_path: string;
          thumb_path: string;
          user_id?: string;
          width?: number | null;
        };
        Update: {
          created_at?: string;
          game_id?: string;
          height?: number | null;
          id?: string;
          position?: number;
          storage_path?: string;
          thumb_path?: string;
          user_id?: string;
          width?: number | null;
        };
        Relationships: [
          {
            foreignKeyName: 'game_images_game_id_fkey';
            columns: ['game_id'];
            isOneToOne: false;
            referencedRelation: 'games';
            referencedColumns: ['id'];
          },
        ];
      };
      games: {
        Row: {
          completeness: Database['public']['Enums']['game_completeness'] | null;
          condition: Database['public']['Enums']['game_condition'] | null;
          created_at: string;
          currency: string;
          current_value: number | null;
          developer: string | null;
          edition: string | null;
          format: Database['public']['Enums']['game_format'];
          genres: string[];
          id: string;
          is_favorite: boolean;
          notes: string | null;
          platform_id: string;
          play_status: Database['public']['Enums']['play_status'];
          publisher: string | null;
          purchase_date: string | null;
          purchase_price: number | null;
          purchased_from: string | null;
          rating: number | null;
          region: Database['public']['Enums']['game_region'] | null;
          release_year: number | null;
          search_text: string | null;
          serial_number: string | null;
          title: string;
          updated_at: string;
          user_id: string;
        };
        Insert: {
          completeness?: Database['public']['Enums']['game_completeness'] | null;
          condition?: Database['public']['Enums']['game_condition'] | null;
          created_at?: string;
          currency?: string;
          current_value?: number | null;
          developer?: string | null;
          edition?: string | null;
          format?: Database['public']['Enums']['game_format'];
          genres?: string[];
          id?: string;
          is_favorite?: boolean;
          notes?: string | null;
          platform_id: string;
          play_status?: Database['public']['Enums']['play_status'];
          publisher?: string | null;
          purchase_date?: string | null;
          purchase_price?: number | null;
          purchased_from?: string | null;
          rating?: number | null;
          region?: Database['public']['Enums']['game_region'] | null;
          release_year?: number | null;
          search_text?: never;
          serial_number?: string | null;
          title: string;
          updated_at?: string;
          user_id?: string;
        };
        Update: {
          completeness?: Database['public']['Enums']['game_completeness'] | null;
          condition?: Database['public']['Enums']['game_condition'] | null;
          created_at?: string;
          currency?: string;
          current_value?: number | null;
          developer?: string | null;
          edition?: string | null;
          format?: Database['public']['Enums']['game_format'];
          genres?: string[];
          id?: string;
          is_favorite?: boolean;
          notes?: string | null;
          platform_id?: string;
          play_status?: Database['public']['Enums']['play_status'];
          publisher?: string | null;
          purchase_date?: string | null;
          purchase_price?: number | null;
          purchased_from?: string | null;
          rating?: number | null;
          region?: Database['public']['Enums']['game_region'] | null;
          release_year?: number | null;
          search_text?: never;
          serial_number?: string | null;
          title?: string;
          updated_at?: string;
          user_id?: string;
        };
        Relationships: [
          {
            foreignKeyName: 'games_platform_id_fkey';
            columns: ['platform_id'];
            isOneToOne: false;
            referencedRelation: 'platforms';
            referencedColumns: ['id'];
          },
        ];
      };
      platforms: {
        Row: {
          id: string;
          manufacturer: string;
          name: string;
          release_year: number | null;
          short_name: string;
          sort_order: number;
        };
        Insert: {
          id: string;
          manufacturer: string;
          name: string;
          release_year?: number | null;
          short_name: string;
          sort_order?: number;
        };
        Update: {
          id?: string;
          manufacturer?: string;
          name?: string;
          release_year?: number | null;
          short_name?: string;
          sort_order?: number;
        };
        Relationships: [];
      };
    };
    Views: {
      [_ in never]: never;
    };
    Functions: {
      collection_stats: {
        Args: never;
        Returns: Json;
      };
    };
    Enums: {
      game_completeness: 'complete' | 'game_box' | 'loose' | 'box_only';
      game_condition: 'sealed' | 'mint' | 'very_good' | 'good' | 'fair' | 'poor';
      game_format: 'physical' | 'digital';
      game_region: 'pal' | 'ntsc_u' | 'ntsc_j' | 'ntsc_k' | 'ntsc_c' | 'region_free' | 'other';
      play_status: 'not_started' | 'playing' | 'completed' | 'abandoned';
    };
    CompositeTypes: {
      [_ in never]: never;
    };
  };
};

type PublicSchema = Database['public'];

export type Tables<T extends keyof PublicSchema['Tables']> = PublicSchema['Tables'][T]['Row'];
export type TablesInsert<T extends keyof PublicSchema['Tables']> =
  PublicSchema['Tables'][T]['Insert'];
export type TablesUpdate<T extends keyof PublicSchema['Tables']> =
  PublicSchema['Tables'][T]['Update'];
export type Enums<T extends keyof PublicSchema['Enums']> = PublicSchema['Enums'][T];
